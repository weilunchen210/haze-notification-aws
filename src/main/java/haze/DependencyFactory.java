
package haze;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * The module containing all dependencies required by the {@link App}.
 */
public class DependencyFactory {

    private DependencyFactory() {}

    public static DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .httpClient(AwsCrtHttpClient.builder().build())
                .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    public static SqsClient sqsClient() {
        return SqsClient.builder()
                .httpClient(AwsCrtHttpClient.builder().build())
                .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    public static SesV2Client sesClient() {
        return SesV2Client.builder()
                .httpClient(AwsCrtHttpClient.builder().build())
                .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    /**
     * @return an instance of DynamoDbEnhancedClient
     */
    public static DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient())
                .build();
    }
}
