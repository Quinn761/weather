package com.weatherhub.ai.tool;

import org.springframework.security.core.context.SecurityContextHolder;

public final class ToolAccess {
    private ToolAccess() {}
    public static boolean allowed(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(item -> permission.equals(item.getAuthority()));
    }
}
