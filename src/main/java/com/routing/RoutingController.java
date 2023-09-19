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

import java.io.IOException;

@Controller("/routing")
public class RoutingController {

    @Inject
    @Client("http://host.docker.internal:8081")  // login-service URL
    private HttpClient loginServiceClient;

    @Inject
    @Client("http://host.docker.internal:8083")  // customer-service URL
    private HttpClient customerServiceClient;


    @Get("/customer")
    public MutableHttpResponse<Object> routeToCustomerService() {
       // String jwt = extractJwtFromRequest();
       // validateJwt(jwt, request);
        System.out.println("Made it GET /customer method");
        // Forward the validated request to the Customer Service
        return HttpResponse.ok();
        //return forwardRequest("/customer", customerServiceClient);
    }

    // Add similar methods for other services

    private String extractJwtFromRequest(HttpRequest<?> request) {
        String authorizationHeader = request.getHeaders().getAuthorization().orElse(null);
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7); // Extract the JWT token
        } else {
            throw new HttpStatusException(HttpStatus.valueOf(401), "JWT token not found in the request");
        }
    }

    private void validateJwt(String jwt, HttpRequest<?> request) {
        if (jwt == null) {
            String username = request.getHeaders().get("username");
            String password = request.getHeaders().get("password");
            if (username == null || password == null) {
                throw new HttpStatusException(HttpStatus.valueOf(401), "Authentication details missing");
            } else {
                // Logic to validate authentication details by calling the login-service
                // You'll need to implement this based on your authentication service
                // Example:
                // loginServiceClient.authenticate(username, password);
            }
        } else {
            // Logic to validate JWT by calling the login-service
            // You'll need to implement this based on your authentication service
            // Example:
            // loginServiceClient.validateJwt(jwt);
        }
    }

    private HttpResponse forwardRequest(String path, HttpClient client) {
        System.out.println("Made it forwardRequest method");

        HttpRequest<?> request = HttpRequest.GET(path);
        try {
            HttpResponse response = client.toBlocking().exchange(request, String.class);
            System.out.println("Response within forwardRequest method: " + response.toString());
            return response;
        } catch (HttpClientResponseException e) {
            throw new HttpStatusException(e.getStatus(), "Request forwarding failed");
        }
    }
}
