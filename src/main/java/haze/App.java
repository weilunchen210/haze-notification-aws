package haze;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Lambda function entry point. You can change to use other pojo type or implement
 * a different RequestHandler.
 *
 * @see <a href=https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html>Lambda Java Handler</a> for more information
 */
public class App implements RequestHandler<Map<String, String>, Void> {

    // Created once per container and reused across invocations
    private static final DynamoDbClient dynamoDbClient = DependencyFactory.dynamoDbClient();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final SqsClient sqsClient = DependencyFactory.sqsClient();
    private static final OpenMeteoApi openMeteoApi = OpenMeteoApi.create();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final String QUEUE_URL = System.getenv("QUEUE_URL");

    // Kuala Lumpur
    private static final double LATITUDE = 3.1412;
    private static final double LONGITUDE = 101.6865;
    private static final int FORECAST_DAYS = 3;

    @Override
    public Void handleRequest(final Map<String, String> input, final Context context) {
        LambdaLogger lambdaLogger = context.getLogger();
        lambdaLogger.log("Start to handle request");

        List<User> users = getAllUsers();

        Map<Long, List<User>> usersByLocation = users.stream()
                .collect(Collectors.groupingBy(User::getOpenMeteoGeolocationId));

        OpenMeteoResponse response;

        for (Map.Entry<Long, List<User>> entry : usersByLocation.entrySet()) {
            long locationId = entry.getKey();
            List<User> usersForLocation = entry.getValue();

            try {
                response = openMeteoApi.getLocationDetails(locationId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch air quality forecast for location ID: " + locationId, e);
            }

            for (User user : usersForLocation) {
                sendToQueue(new AirQualityEmailMessageRequest(user, response));
            }
        }

        lambdaLogger.log("Sent " + users.size() + " messages to SQS");
        return null;
    }

    private List<User> getAllUsers() {
        return enhancedClient.table(TABLE_NAME, TableSchema.fromBean(User.class))
                .scan()
                .items()
                .stream()
                .toList();
    }

    private void sendToQueue(AirQualityEmailMessageRequest message) {
        String body;
        try {
            body = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message for user " + message.getUser().getId(), e);
        }

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .messageBody(body)
                .build());
    }
}
