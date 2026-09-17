package com.duoc.sumativa.bff.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Generic pass-through proxy used by the BFF controllers.
 *
 * It forwards the incoming request (method, body) to the target serverless
 * function URL and returns the function's response (status + body) as-is.
 * This is the core of the BFF orchestration/pass-through layer required by
 * the assignment: the BFF itself never talks to the database.
 */
@Service
public class ProxyService {

    private final RestTemplate restTemplate;

    public ProxyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> forward(String url, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(url, method, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            // Relay the exact status/body returned by the function, instead of
            // wrapping it in a generic 500 - keeps the pass-through contract.
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach downstream function: " + ex.getMessage() + "\"}");
        }
    }

    public ResponseEntity<String> forward(String url, HttpMethod method) {
        return forward(url, method, null);
    }
}
