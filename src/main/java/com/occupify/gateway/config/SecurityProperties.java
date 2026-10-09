package com.occupify.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private List<String> whitelistedPaths = new ArrayList<>(List.of(
            "/auth/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**",
            "/scalar/**",
            "/scalar",
            "/docs/**",
            "/docs",
            "/fallback/**"
    ));

    private List<String> adminRestrictedPaths = new ArrayList<>(List.of(
            "/admin/**",
            "/audit-logs/**",
            "/reports/**"
    ));

    public List<String> getWhitelistedPaths() {
        return whitelistedPaths;
    }

    public void setWhitelistedPaths(List<String> whitelistedPaths) {
        this.whitelistedPaths = whitelistedPaths;
    }

    public List<String> getAdminRestrictedPaths() {
        return adminRestrictedPaths;
    }

    public void setAdminRestrictedPaths(List<String> adminRestrictedPaths) {
        this.adminRestrictedPaths = adminRestrictedPaths;
    }
}
