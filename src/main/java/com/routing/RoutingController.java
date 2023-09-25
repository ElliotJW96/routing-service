package com.routing;

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

    @Post("/login")
    public HttpResponse<String> login(@Body LoginRequest loginRequest) {

        LOG.info("Login service /login call attempt.");

        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Authentication details missing");
        }

        try {
            return loginServiceClient.toBlocking()
                    .exchange(HttpRequest.POST("/login", loginRequest), String.class);
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            throw new HttpStatusException(e.getStatus(), "Error during login");
        }
    }

    @Get("/customer")
    public HttpResponse<String> routeToCustomerService(HttpRequest<?> request) {
        LOG.info("Routing service call attempt to customer-service");
        String customerId = validateJwt(request);

        // Construct a new request with the customerId header
        MutableHttpRequest<Object> modifiedRequest = HttpRequest.GET("/customer")
                .header("customerId", customerId);
        LOG.info("Modified request headers: ");
        modifiedRequest.getHeaders().forEach((key, value) -> LOG.info(key + ": " + value));

        return forwardRequest(modifiedRequest, customerServiceClient, "/customer");
    }

    @Get("/mortgages")
    public HttpResponse<String> routeToMortgageService(HttpRequest<?> request) {
        LOG.info("Routing service call attempt to mortgage-service");
        String customerId = validateJwt(request);

        // Construct a new request with the customerId header
        MutableHttpRequest<Object> modifiedRequest = HttpRequest.GET("/mortgages")
                .header("customerId", customerId);
        LOG.info("Modified request headers: ");
        modifiedRequest.getHeaders().forEach((key, value) -> LOG.info(key + ": " + value));

        return forwardRequest(modifiedRequest, mortgageServiceClient, "/mortgages");
    }

    @Get("/product")
    public HttpResponse<String> routeToProductService(HttpRequest<?> request, @QueryValue String mortgageId){
        LOG.info("Routing service call attempt to product-service.");
        validateJwt(request);
        return forwardRequest(request, productServiceClient, "/product");
    }


    private HttpResponse<String> forwardRequest(HttpRequest<?> originalRequest, HttpClient client, String path) {
// Create the request while preserving the original request's query parameters
        UriBuilder uriBuilder = UriBuilder.of(path);
        originalRequest.getParameters().forEach((name, values) -> {
            values.forEach(value -> uriBuilder.queryParam(name, value));
        });

        HttpRequest<?> request = HttpRequest.create(originalRequest.getMethod(), uriBuilder.build().toString())
                .headers(headers -> {
                    for (String name : originalRequest.getHeaders().names()) {
                        originalRequest.getHeaders().getAll(name).forEach(value -> headers.add(name, value));
                    }
                });

        try {
            return client.toBlocking().exchange(request, String.class);
        } catch (HttpClientResponseException e) {
            LOG.error("Error forwarding request to path: " + path + ". Status: " + e.getStatus() + ". Error: " + e.getMessage());
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(e.getStatus(), "Unauthorized request when forwarding to " + path);
            }
            throw new HttpStatusException(e.getStatus(), "Request forwarding to " + path + " failed: " + e.getMessage());
        } catch (Exception e) {
            // Catch other unexpected exceptions
            LOG.error("Unexpected error while forwarding request to path: " + path, e);
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during request forwarding");
        }
    }


    private String validateJwt(HttpRequest<?> request) {
        String jwt = request.getHeaders().getAuthorization().orElse(null);
        if (jwt == null) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "JWT missing");
        }
        try {
            HttpResponse<?> authResponse = loginServiceClient.toBlocking()
                    .exchange(HttpRequest.POST("/auth", "")
                            .header("Authorization", jwt), String.class);
            return authResponse.getHeaders().get("customerId");
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT");
            }
            throw new HttpStatusException(e.getStatus(), "Error during JWT validation");
        }

    }


}
