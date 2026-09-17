package com.duoc.sumativa.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the functions.* configuration keys from application.yml.
 */
@ConfigurationProperties(prefix = "functions")
public class FunctionsProperties {

    private String usersBaseUrl;
    private String rolesGraphqlUrl;

    public String getUsersBaseUrl() {
        return usersBaseUrl;
    }

    public void setUsersBaseUrl(String usersBaseUrl) {
        this.usersBaseUrl = usersBaseUrl;
    }

    public String getRolesGraphqlUrl() {
        return rolesGraphqlUrl;
    }

    public void setRolesGraphqlUrl(String rolesGraphqlUrl) {
        this.rolesGraphqlUrl = rolesGraphqlUrl;
    }
}
