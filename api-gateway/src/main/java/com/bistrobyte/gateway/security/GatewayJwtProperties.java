package com.bistrobyte.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Edge security configuration: signing secret, public paths and per-path role rules. */
@ConfigurationProperties(prefix = "bistrobyte.security.jwt")
public class GatewayJwtProperties {

    /** Must match the secret used by the user-service that mints the tokens. */
    private String secret = "bistrobyte-super-secret-signing-key-change-me-in-production";

    private String issuer = "bistrobyte-user-service";

    /** Ant-style paths served without a token (login, registration, docs, health). */
    private List<String> publicPaths = new ArrayList<>();

    /** Coarse edge authorisation rules; services re-check with method security. */
    private List<RoleRule> roleRules = new ArrayList<>();

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<RoleRule> getRoleRules() {
        return roleRules;
    }

    public void setRoleRules(List<RoleRule> roleRules) {
        this.roleRules = roleRules;
    }

    /** "Requests matching {@code pattern} with method {@code methods} need one of {@code roles}." */
    public static class RoleRule {

        private String pattern;
        private List<String> methods = new ArrayList<>();
        private List<String> roles = new ArrayList<>();

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
