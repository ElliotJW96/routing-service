package com.routing;

import com.routing.models.DebitInstructionDay;
import com.routing.models.LoginRequest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Controller responsible for routing requests to various services.
 */
@Controller
public class RoutingController {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingController.class);

    @Inject
    @Client("http://host.docker.internal:8081")  // login-service URL
    private HttpClient loginServiceClient;

    @Inject
    @Client("http://host.docker.internal:8083")  // customer-service URL
    private HttpClient customerServiceClient;

    @Inject
    @Client("http://host.docker.internal:8084")  // mortgage-service URL
    private HttpClient mortgageServiceClient;

    @Inject
    @Client("http://host.docker.internal:8085")  // product-service URL
    private HttpClient productServiceClient;

    @Inject
    @Client("http://host.docker.internal:8086")  // debit-instruction-service URL
    private HttpClient debitInstructionServiceClient;

    /**
     * Route to login service for authentication.
     *
     * @param loginRequest The login credentials.
     * @return HttpResponse after attempting the login.
     */
    @Post("/login")
    public HttpResponse<String> login(@Body LoginRequest loginRequest) {
        LOG.info("Login service /login call attempt with username: {}", loginRequest.getUsername());

        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            LOG.warn("Authentication details missing in login request.");
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Authentication details missing");
        }

        try {
            HttpResponse<String> response = loginServiceClient.toBlocking()
                    .exchange(HttpRequest.POST("/login", loginRequest), String.class);
            LOG.info("Login service responded with status: {}", response.getStatus());
            return response;
        } catch (HttpClientResponseException e) {
            LOG.error("Error during login with status: {}. Error: {}", e.getStatus(), e.getMessage());
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            throw new HttpStatusException(e.getStatus(), "Error during login");
        }
    }

    /**
     * Route to customer service with customerId.
     *
     * @param request The original request.
     * @return HttpResponse from the customer service.
     */
    @Get("/customer")
    public HttpResponse<String> routeToCustomerService(HttpRequest<?> request) {
        LOG.info("Routing service call attempt to customer-service");
        String customerId = validateJwt(request);

        MutableHttpRequest<Object> modifiedRequest = HttpRequest.GET("/customer")
                .header("customerId", customerId);

        LOG.info("Modified request headers for customer-service: ");
        modifiedRequest.getHeaders().forEach((key, value) -> LOG.info("{}: {}", key, value));

        return forwardRequest(modifiedRequest, customerServiceClient);
    }

    /**
     * Route to mortgage service with customerId.
     *
     * @param request The original request.
     * @return HttpResponse from the mortgage service.
     */
    @Get("/mortgages")
    public HttpResponse<String> routeToMortgageService(HttpRequest<?> request) {
        LOG.info("Routing service call attempt to mortgage-service");
        String customerId = validateJwt(request);

        MutableHttpRequest<Object> modifiedRequest = HttpRequest.GET("/mortgages")
                .header("customerId", customerId);
        LOG.info("Modified request headers for mortgage-service: ");
        modifiedRequest.getHeaders().forEach((key, value) -> LOG.info("{}: {}", key, value));

        return forwardRequest(modifiedRequest, mortgageServiceClient);
    }

    /**
     * Route to product service.
     *
     * @param request The original request.
     * @param mortgageId The mortgage ID.
     * @return HttpResponse from the product service.
     */
    @Get("/product")
    public HttpResponse<String> routeToProductService(HttpRequest<?> request, @QueryValue String mortgageId) {
        LOG.info("Routing service call attempt to product-service with mortgageId: {}", mortgageId);
        validateJwt(request);

        return forwardRequest(request, productServiceClient);
    }

    /**
     * Route to debit instruction service with customerId and mortgageId.
     *
     * @param request The original request.
     * @param mortgageId The mortgage ID.
     * @return HttpResponse from the debit instruction service.
     */
    @Get("/debitinstruction")
    public HttpResponse<String> routeToDebitInstructionService(HttpRequest<?> request, @QueryValue String mortgageId) {
        LOG.info("Routing service call attempt to GET debit-instruction-service with mortgageId: {}", mortgageId);
        String customerId = validateJwt(request);

        UriBuilder uriBuilder = UriBuilder.of("/debitinstruction")
                .queryParam("customerId", customerId)
                .queryParam("mortgageId", mortgageId);

        MutableHttpRequest<Object> modifiedRequest = HttpRequest.GET(uriBuilder.build());
        LOG.info("Modified request for debit-instruction-service: {}", modifiedRequest);

        return forwardRequest(modifiedRequest, debitInstructionServiceClient);
    }

    /**
     * Route to update debit instruction service with customerId and mortgageId.
     *
     * @param request The original request.
     * @param mortgageId The mortgage ID.
     * @param body The debit instruction day body.
     * @return HttpResponse from the update debit instruction service.
     */
    @Put("/debitinstruction")
    public HttpResponse<?> routeToUpdateDebitInstructionService(
            HttpRequest<?> request, @QueryValue String mortgageId, @Body DebitInstructionDay body
    ) {
        LOG.info("Routing service call attempt to PUT debit-instruction-service with mortgageId: {}", mortgageId);
        String customerId = validateJwt(request);

        UriBuilder uriBuilder = UriBuilder.of("/debitinstruction")
                .queryParam("customerId", customerId)
                .queryParam("mortgageId", mortgageId);

        MutableHttpRequest<Object> modifiedRequest = HttpRequest.PUT(uriBuilder.build(), body);
        LOG.info("Modified PUT request for debit-instruction-service: {}", modifiedRequest);

        return forwardRequest(modifiedRequest, debitInstructionServiceClient);
    }

    /**
     * Forwards a request to a target service and returns the response.
     *
     * @param originalRequest The original request that has been pre-configured with the required details.
     * @param client The HTTP client for the target service.
     * @return HttpResponse from the target service.
     */
    private HttpResponse<String> forwardRequest(HttpRequest<?> originalRequest, HttpClient client) {
        String path = originalRequest.getUri().toString();
        String method = originalRequest.getMethod().toString();
        String headers = originalRequest.getHeaders().toString();

        Optional<?> bodyOptional = originalRequest.getBody();
        String bodyString = bodyOptional.map(Object::toString).orElse("No Body");

        LOG.info("Preparing to forward request:");
        LOG.info("Method: {}", method);
        LOG.info("Path: {}", path);
        LOG.info("Headers: {}", headers);
        LOG.info("Body: {}", bodyString);

        try {
            // Forward the original request directly to the routed service.
            HttpResponse<String> response = client.toBlocking().exchange(originalRequest, String.class);
            LOG.info("Request forwarded to path: {} with response status: {}", path, response.getStatus());
            return response;
        } catch (HttpClientResponseException e) {
            LOG.error("Error forwarding request to path: {}. Status: {}. Error: {}", path, e.getStatus(), e.getMessage());
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(e.getStatus(), "Unauthorized request when forwarding to " + path);
            }
            throw new HttpStatusException(e.getStatus(), "Request forwarding to " + path + " failed: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while forwarding request to path: {}", path, e);
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during request forwarding");
        }
    }


    /**
     * Validates the JWT token and returns the associated customerId.
     *
     * @param request The original request containing the JWT.
     * @return customerId associated with the JWT.
     */
    private String validateJwt(HttpRequest<?> request) {
        String jwt = request.getHeaders().getAuthorization().orElse(null);
        if (jwt == null) {
            LOG.warn("JWT missing in the request headers.");
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "JWT missing");
        }
        try {
            HttpResponse<?> authResponse = loginServiceClient.toBlocking()
                    .exchange(HttpRequest.POST("/auth", "")
                            .header("Authorization", jwt), String.class);
            return authResponse.getHeaders().get("customerId");
        } catch (HttpClientResponseException e) {
            LOG.error("Error during JWT validation. Status: {}. Error: {}", e.getStatus(), e.getMessage());
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT");
            }
            throw new HttpStatusException(e.getStatus(), "Error during JWT validation");
        }
    }
}
