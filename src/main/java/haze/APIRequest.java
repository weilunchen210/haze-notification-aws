package haze;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Map;

/**
 * REST API for users, behind API Gateway (Lambda proxy integration).
 *
 * <pre>
 * GET    /users        list all users
 * GET    /users/{id}   get one user
 * POST   /users        create a user   body: {"id":1,"name":"...","email":"..."}
 * DELETE /users/{id}   delete a user
 * </pre>
 */
public class APIRequest implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Created once per container and reused across invocations
    private static final DynamoDbClient dynamoDbClient = DependencyFactory.dynamoDbClient();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final DynamoDbTable<User> userTable =
            enhancedClient.table(TABLE_NAME, TableSchema.fromBean(User.class));
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OpenMeteoApi openMeteoApi = OpenMeteoApi.create();

    private static final Map<String, String> HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // getResource() is the route template, e.g. "/users/{id}", not the actual path "/users/1"
        String route = input.getHttpMethod() + " " + input.getResource();

        try {
            return switch (route) {
                case "GET /users" -> json(200, getAllUsers());
                case "GET /users/{id}" -> getUser(pathId(input));
                case "POST /users" -> createUser(input.getBody());
                case "DELETE /users/{id}" -> deleteUser(pathId(input));
                default -> error(404, "Route not found: " + route);
            };
        } catch (BadRequestException e) {
            return error(400, e.getMessage());
        } catch (Exception e) {
            // Never let an exception escape: API Gateway would turn it into a bare 502
            context.getLogger().log("Unhandled error on " + route + ": " + e + "\n");
            return error(500, "Internal server error");
        }
    }

    private List<User> getAllUsers() {
        return userTable.scan()
                .items()
                .stream()
                .toList();
    }

    private APIGatewayProxyResponseEvent getUser(long id) {
        User user = userTable.getItem(key(id));
        return user == null ? error(404, "User " + id + " not found") : json(200, user);
    }

    private APIGatewayProxyResponseEvent createUser(String body) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("Request body is required");
        }

        User user;
        try {
            user = objectMapper.readValue(body, User.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid JSON body");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new BadRequestException("A valid email is required");
        }

        GeocodingResponse geocodingResponse = openMeteoApi.getLocationDetails(user.getOpenMeteoGeolocationId());
        if (geocodingResponse == null) {
            throw new BadRequestException("Invalid Open-Meteo geolocation ID: " + user.getOpenMeteoGeolocationId());
        }

        try {
            // Only write if no user with this id exists, so POST never silently overwrites
            userTable.putItem(PutItemEnhancedRequest.builder(User.class)
                    .item(user)
                    .conditionExpression(Expression.builder().expression("attribute_not_exists(id)").build())
                    .build());
        } catch (ConditionalCheckFailedException e) {
            return error(409, "User " + user.getId() + " already exists");
        }
        return json(201, user);
    }

    private APIGatewayProxyResponseEvent deleteUser(long id) {
        User deleted = userTable.deleteItem(key(id));
        if (deleted == null) {
            return error(404, "User " + id + " not found");
        }
        return new APIGatewayProxyResponseEvent().withStatusCode(204).withHeaders(HEADERS);
    }

    private static long pathId(APIGatewayProxyRequestEvent input) {
        String raw = input.getPathParameters() == null ? null : input.getPathParameters().get("id");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestException("User id must be a number");
        }
    }

    private static Key key(long id) {
        return Key.builder().partitionValue(id).build();
    }

    private static APIGatewayProxyResponseEvent json(int statusCode, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(HEADERS)
                    .withBody(objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

    private static APIGatewayProxyResponseEvent error(int statusCode, String message) {
        return json(statusCode, Map.of("error", message));
    }

    /** Client sent something invalid; mapped to HTTP 400. */
    private static class BadRequestException extends RuntimeException {
        BadRequestException(String message) {
            super(message);
        }
    }
}
