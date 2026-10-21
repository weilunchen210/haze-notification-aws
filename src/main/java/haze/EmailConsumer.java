package haze;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Consumes {@link AirQualityEmailMessageRequest} messages from SQS and emails each user
 * their air quality forecast through SES.
 */
public class EmailConsumer implements RequestHandler<SQSEvent, SQSBatchResponse> {

    // Created once per container and reused across invocations
    private static final SesV2Client sesClient = DependencyFactory.sesClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SENDER_EMAIL = System.getenv("SENDER_EMAIL");

    @Override
    public SQSBatchResponse handleRequest(final SQSEvent event, final Context context) {
        LambdaLogger lambdaLogger = context.getLogger();
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
            try {
                AirQualityEmailMessageRequest request =
                        objectMapper.readValue(sqsMessage.getBody(), AirQualityEmailMessageRequest.class);
                sendEmail(request, lambdaLogger);
            } catch (Exception e) {
                lambdaLogger.log("Failed to process message " + sqsMessage.getMessageId() + ": " + e);
                failures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
            }
        }

        return new SQSBatchResponse(failures);
    }

    private void sendEmail(AirQualityEmailMessageRequest request, LambdaLogger lambdaLogger) {
        User user = request.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            lambdaLogger.log("Skipping message with no recipient email");
            return;
        }

        Map<String, Integer> dailyPeak = dailyPeakAqi(request.getAirQuality());

        sesClient.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(SENDER_EMAIL)
                .destination(Destination.builder().toAddresses(user.getEmail()).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(text("Your air quality forecast"))
                                .body(Body.builder()
                                        .text(text(buildTextBody(user, dailyPeak)))
                                        .html(text(buildHtmlBody(user, dailyPeak)))
                                        .build())
                                .build())
                        .build())
                .build());

        lambdaLogger.log("Sent air quality email to user " + user.getId());
    }

    /**
     * Highest US AQI (PM2.5) for each day, keyed by date (yyyy-MM-dd) in forecast order.
     */
    static Map<String, Integer> dailyPeakAqi(OpenMeteoResponse airQuality) {
        Map<String, Integer> dailyPeak = new LinkedHashMap<>();
        List<String> times = airQuality.getHourly().getTime();
        List<Integer> aqi = airQuality.getHourly().getUsAqiPm25();

        for (int i = 0; i < times.size(); i++) {
            if (aqi.get(i) != null) {
                dailyPeak.merge(times.get(i).substring(0, 10), aqi.get(i), Math::max);
            }
        }
        return dailyPeak;
    }

    static String category(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very Unhealthy";
        return "Hazardous";
    }

    static String buildTextBody(User user, Map<String, Integer> dailyPeak) {
        StringBuilder sb = new StringBuilder("Hi " + user.getName() + ",\n\n"
                + "Here is the PM2.5 air quality forecast (peak US AQI per day, GMT):\n\n");
        dailyPeak.forEach((date, aqi) ->
                sb.append(date).append(": ").append(aqi).append(" (").append(category(aqi)).append(")\n"));
        return sb.toString();
    }

    static String buildHtmlBody(User user, Map<String, Integer> dailyPeak) {
        StringBuilder sb = new StringBuilder("<p>Hi " + escape(user.getName()) + ",</p>"
                + "<p>Here is the PM2.5 air quality forecast (peak US AQI per day, GMT):</p>"
                + "<table border=\"1\" cellpadding=\"6\" style=\"border-collapse:collapse\">"
                + "<tr><th>Date</th><th>Peak AQI</th><th>Category</th></tr>");
        dailyPeak.forEach((date, aqi) -> sb.append("<tr><td>").append(date)
                .append("</td><td>").append(aqi)
                .append("</td><td>").append(category(aqi)).append("</td></tr>"));
        return sb.append("</table>").toString();
    }

    private static Content text(String data) {
        return Content.builder().data(data).charset("UTF-8").build();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
