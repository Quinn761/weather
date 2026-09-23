package com.weatherhub.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAuthorizationServiceTest {
    private final AgentAuthorizationService service = new AgentAuthorizationService();

    @Test
    void allowsReadOnlyToolWithRequiredAuthority() {
        var auth = new UsernamePasswordAuthenticationToken("1", "n/a", List.of(new SimpleGrantedAuthority("gis:read")));
        assertTrue(service.authorize("list_gis_features", auth).allowed());
    }

    @Test
    void deniesUnknownAndWriteToolsRegardlessOfModelOrUserPermission() {
        var auth = new UsernamePasswordAuthenticationToken("1", "n/a", List.of(new SimpleGrantedAuthority("user:write")));
        assertFalse(service.authorize("model_suggested_delete", auth).allowed());
        assertFalse(service.authorize("delete_user", auth).allowed());
        assertFalse(service.authorize("publish_alert", auth).allowed());
    }
}
