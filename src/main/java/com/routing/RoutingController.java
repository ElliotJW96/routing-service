package com.routing;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;

@Controller
public class RoutingController {

    @Inject
    @Client("http://host.docker.internal:8081")  // login-service URL
    private HttpClient loginServiceClient;

    @Inject
    @Client("http://host.docker.internal:8083")  // customer-service URL
    private HttpClient customerServiceClient;

    @Get("/customer")
    public HttpResponse routeToCustomerService(HttpRequest<?> request) {
        validate(request);
        System.out.println("Made it GET /customer method");
        // Forward the validated request to the Customer Service
        return forwardRequest("/customer", customerServiceClient);
    }

    private void validate(HttpRequest<?> request) {
        String jwt = request.getHeaders().getAuthorization().orElse(null);
        if (jwt == null) {
            String username = request.getHeaders().get("username");
            String password = request.getHeaders().get("password");
            if (username == null || password == null) {
                throw new HttpStatusException(HttpStatus.valueOf(401), "Authentication details missing");
            } else {
                MutableHttpResponse<String> response = (MutableHttpResponse<String>) loginServiceClient.toBlocking()
                        .exchange(HttpRequest.POST("/login", "")
                                .header("username", username)
                                .header("password", password), String.class);
                if (response.getStatus() != HttpStatus.CREATED) {
                    throw new HttpStatusException(HttpStatus.valueOf(401), "Invalid credentials");
                }
            }
        } else {
            MutableHttpResponse<String> response = (MutableHttpResponse<String>) loginServiceClient.toBlocking()
                    .exchange(HttpRequest.GET("/auth")
                            .header("Authorization", "Bearer " + jwt), String.class);
            if (response.getStatus() != HttpStatus.CREATED) {
                throw new HttpStatusException(HttpStatus.valueOf(401), "Invalid JWT");
            }
        }
    }

    private MutableHttpResponse<String> forwardRequest(String path, HttpClient client) {
        System.out.println("Made it forwardRequest method");
        HttpRequest<?> request = HttpRequest.GET(path);
        try {
            MutableHttpResponse<String> response = (MutableHttpResponse<String>) client.toBlocking().exchange(request, String.class);
            System.out.println("Response within forwardRequest method: " + response.toString());
            return response;
        } catch (HttpClientResponseException e) {
            throw new HttpStatusException(e.getStatus(), "Request forwarding failed");
        }
    }
}
