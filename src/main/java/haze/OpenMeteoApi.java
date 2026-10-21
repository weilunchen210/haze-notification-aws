package haze;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for the Open-Meteo air quality API.
 *
 * @see <a href="https://open-meteo.com/en/docs/air-quality-api">Open-Meteo Air Quality API</a>
 */
public class OpenMeteoApi {

    private static final String BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private OpenMeteoApi(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public static OpenMeteoApi create() {
        return new OpenMeteoApi(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper());
    }

    /**
     * Fetches the hourly US AQI (PM2.5) forecast for a location.
     */
    public OpenMeteoResponse getAirQuality(double latitude, double longitude, int forecastDays)
            throws IOException, InterruptedException {
        URI uri = URI.create(BASE_URL + "?latitude=" + latitude + "&longitude=" + longitude + "&hourly=us_aqi_pm2_5" + "&forecast_days=" + forecastDays);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Open-Meteo request failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), OpenMeteoResponse.class);
    }

    public OpenMeteoResponse getLocationDetails(long locationId) {
        URI uri = URI.create(BASE_URL + "?id=" + locationId);

        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Open-Meteo request failed: HTTP " + response.statusCode() + " - " + response.body());
            }
            return objectMapper.readValue(response.body(), OpenMeteoResponse.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch location details", e);
        }

    }
}
