package haze;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponse {

    private double latitude;
    private double longitude;
    private String timezone;
    private Hourly hourly;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hourly {
        private List<String> time;

        // Integer rather than int: Open-Meteo returns null for hours with no data
        @JsonProperty("us_aqi_pm2_5")
        private List<Integer> usAqiPm25;
    }
}
