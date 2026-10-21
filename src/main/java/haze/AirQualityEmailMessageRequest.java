package haze;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQS message body: a user together with the air quality forecast.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AirQualityEmailMessageRequest {

    private User user;
    private OpenMeteoResponse airQuality;
}
