package com.duoc.sumativa.bff.controller;

import com.duoc.sumativa.bff.config.FunctionsProperties;
import com.duoc.sumativa.bff.service.ProxyService;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * BFF controller exposing the users REST contract.
 *
 * Every method simply forwards the request to users-function and returns
 * its response as-is: this controller never accesses the database.
 */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final ProxyService proxyService;
    private final FunctionsProperties functionsProperties;

    public UsersController(ProxyService proxyService, FunctionsProperties functionsProperties) {
        this.proxyService = proxyService;
        this.functionsProperties = functionsProperties;
    }

    private String baseUrl() {
        return functionsProperties.getUsersBaseUrl();
    }

    @GetMapping
    public ResponseEntity<String> listUsers() {
        return proxyService.forward(baseUrl() + "/users", HttpMethod.GET);
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getUser(@PathVariable("id") Long id) {
        return proxyService.forward(baseUrl() + "/users/" + id, HttpMethod.GET);
    }

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody String body) {
        return proxyService.forward(baseUrl() + "/users", HttpMethod.POST, body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateUser(@PathVariable("id") Long id, @RequestBody String body) {
        return proxyService.forward(baseUrl() + "/users/" + id, HttpMethod.PUT, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable("id") Long id) {
        return proxyService.forward(baseUrl() + "/users/" + id, HttpMethod.DELETE);
    }

    @PostMapping("/{id}/roles/{roleId}")
    public ResponseEntity<String> assignRole(@PathVariable("id") Long id, @PathVariable("roleId") Long roleId) {
        return proxyService.forward(baseUrl() + "/users/" + id + "/roles/" + roleId, HttpMethod.POST);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<String> removeRole(@PathVariable("id") Long id, @PathVariable("roleId") Long roleId) {
        return proxyService.forward(baseUrl() + "/users/" + id + "/roles/" + roleId, HttpMethod.DELETE);
    }
}
