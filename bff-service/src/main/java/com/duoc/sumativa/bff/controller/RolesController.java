package com.duoc.sumativa.bff.controller;

import com.duoc.sumativa.bff.service.RolesGraphqlService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST facade for roles.
 * Internally, every operation is translated to GraphQL and sent to roles-function.
 */
@RestController
@RequestMapping("/api/roles")
public class RolesController {

    private final RolesGraphqlService rolesGraphqlService;

    public RolesController(RolesGraphqlService rolesGraphqlService) {
        this.rolesGraphqlService = rolesGraphqlService;
    }

    @GetMapping
    public ResponseEntity<String> listRoles() {
        return rolesGraphqlService.listRoles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getRole(@PathVariable("id") Long id) {
        return rolesGraphqlService.getRole(id);
    }

    @PostMapping
    public ResponseEntity<String> createRole(@RequestBody JsonNode body) {
        String name = body.hasNonNull("name") ? body.get("name").asText() : null;
        String description = body.hasNonNull("description") ? body.get("description").asText() : null;
        return rolesGraphqlService.createRole(name, description);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateRole(@PathVariable("id") Long id, @RequestBody JsonNode body) {
        String name = body.hasNonNull("name") ? body.get("name").asText() : null;
        String description = body.hasNonNull("description") ? body.get("description").asText() : null;
        return rolesGraphqlService.updateRole(id, name, description);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRole(@PathVariable("id") Long id) {
        return rolesGraphqlService.deleteRole(id);
    }
}
