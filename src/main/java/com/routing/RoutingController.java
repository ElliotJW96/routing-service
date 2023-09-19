package com.routing;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
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
        validateJwt(request);
        return forwardRequest(request, customerServiceClient, "/customer");
    }

    private void validateJwt(HttpRequest<?> request) {
        String jwt = request.getHeaders().getAuthorization().orElse(null);
        if (jwt == null) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "JWT missing");
        }
        try {
            loginServiceClient.toBlocking()
                    .exchange(HttpRequest.POST("/auth", "")
                            .header("Authorization", jwt), String.class);
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT");
            }
            throw new HttpStatusException(e.getStatus(), "Error during JWT validation");
        }
    }

    @Post("/login")
    public HttpResponse<String> login(HttpRequest<?> request) {
        String username = request.getHeaders().get("username");
        String password = request.getHeaders().get("password");

        if (username == null || password == null) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Authentication details missing");
        }

        try {
            return loginServiceClient.toBlocking()
                    .exchange(HttpRequest.POST("/login", "")
                            .header("username", username)
                            .header("password", password), String.class);
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            throw new HttpStatusException(e.getStatus(), "Error during login");
        }
    }

    private HttpResponse<String> forwardRequest(HttpRequest<?> originalRequest, HttpClient client, String path) {
        HttpRequest<?> request = HttpRequest.create(originalRequest.getMethod(), path);
        try {
            return client.toBlocking().exchange(request, String.class);
        } catch (HttpClientResponseException e) {
            throw new HttpStatusException(e.getStatus(), "Request forwarding failed");
        }
    }
}
