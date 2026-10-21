package haze;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@NoArgsConstructor
@DynamoDbBean
public class User {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private long id;
    private String name;
    private String email;
    private long openMeteoGeolocationId;
}
