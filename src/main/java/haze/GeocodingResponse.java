package haze;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single location from the Open-Meteo geocoding API.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeocodingResponse {

    private long id;
    private String name;
    private double latitude;
    private double longitude;
    private String timezone;
    private String country;

    // Wrapper types: Open-Meteo omits these for some locations
    private Double elevation;
    private Long population;

    @JsonProperty("feature_code")
    private String featureCode;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("country_id")
    private Long countryId;

    @JsonProperty("admin1_id")
    private Long admin1Id;

    @JsonProperty("admin2_id")
    private Long admin2Id;

    private String admin1;
    private String admin2;
}
