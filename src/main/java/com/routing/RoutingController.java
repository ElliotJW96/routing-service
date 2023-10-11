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
     * @param request The original incoming request.
     * @param loginRequest The login credentials.
     * @return HttpResponse after attempting the login.
     */
    @Post("/login")
    public HttpResponse<String> login(HttpRequest<?> request, @Body LoginRequest loginRequest) {
        LOG.info("Login service /login call attempt with username: {}", loginRequest.getUsername());

        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            LOG.warn("Authentication details missing in login request.");
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Authentication details missing");
        }

        MutableHttpRequest<LoginRequest> modifiedRequest = request.mutate()
                .body(loginRequest);

        try {
            HttpResponse<String> response = loginServiceClient.toBlocking()
                    .exchange(modifiedRequest, String.class);
            LOG.info("Login service responded with status: {}\n", response.getStatus());

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
     * Routes the incoming request to the customer service after adding the customerId header.
     *
     * @param request The original incoming request.
     * @return HttpResponse from the customer service.
     */
    @Get("/customer")
    public HttpResponse<String> routeToCustomerService(HttpRequest<?> request) {
        LOG.info("Routing service call attempt to customer-service");
        String customerId = validateJwt(request);

        MutableHttpRequest<?> modifiedRequest = request.mutate()
                .header("customerId", customerId);

        return forwardRequest(modifiedRequest, customerServiceClient);
    }

    /**
     * Routes the incoming request to the mortgage service after adding the customerId header.
     *
     * @param request The original incoming request.
     * @return HttpResponse from the mortgage service.
     */
    @Get("/mortgages")
    public HttpResponse<String> routeToMortgageService(HttpRequest<?> request) {
        LOG.info("Routing service call attempt to mortgage-service");
        String customerId = validateJwt(request);

        MutableHttpRequest<?> modifiedRequest = request.mutate()
                .header("customerId", customerId);

        return forwardRequest(modifiedRequest, mortgageServiceClient);
    }

    /**
     * Routes the incoming request to the product service without modifications.
     *
     * @param request    The original incoming request.
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
     * Routes the incoming request to the debit instruction service after modifying the URI with
     * customerId and mortgageId as query parameters.
     *
     * @param request   The original incoming request.
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

        MutableHttpRequest<?> modifiedRequest = request.mutate()
                .uri(uriBuilder.build());

        return forwardRequest(modifiedRequest, debitInstructionServiceClient);
    }

    /**
     * Routes the incoming request to update the debit instruction service after modifying the URI with
     * customerId and mortgageId as query parameters.
     *
     * @param request   The original incoming request.
     * @param mortgageId The mortgage ID.
     * @param body     The debit instruction day body.
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

        MutableHttpRequest<?> modifiedRequest = request.mutate()
                .uri(uriBuilder.build())
                .body(body);

        return forwardRequest(modifiedRequest, debitInstructionServiceClient);
    }


    /**
     * Forwards a request to a target service and returns the response.
     *
     * @param request The original request that has been pre-configured with the required details.
     * @param client The HTTP client for the target service.
     * @return HttpResponse from the target service.
     */
    private HttpResponse<String> forwardRequest(HttpRequest<?> request, HttpClient client) {

        //Logging
        String path = request.getUri().toString();
        String method = request.getMethod().toString();

        LOG.info("Preparing to forward request:");
        LOG.info("Method: {}", method);
        LOG.info("Path: {}", path);

        //Handling headers for logging, without exposing JWT in logs.
        request.getHeaders().forEach((name, values) -> {
            values.forEach(value -> {
                if ("Authorization".equalsIgnoreCase(name)) {
                    LOG.info("{}: Masked in logs", name, value);
                } else {
                    LOG.info("{}: {}", name, value);
                }
            });
        });
        LOG.info("\n");

        try {
            // Forward the original request directly to the routed service.
            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
            LOG.info("Request forwarded to path: {} with response status: {}", path, response.getStatus());
            return response;
        } catch (HttpClientResponseException e) {
            LOG.error("Error forwarding request to path: {}. Status: {}. Error: {}", path, e.getStatus(), e.getMessage());
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(e.getStatus(), "Unauthorized request when forwarding to " + path);
            }
            throw new HttpStatusException(e.getStatus(), "Request forwarding to {} failed" + path + " failed: " + e.getMessage());
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
