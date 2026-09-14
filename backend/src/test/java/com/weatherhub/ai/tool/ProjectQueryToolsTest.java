package com.weatherhub.ai.tool;

import com.weatherhub.user.User;
import com.weatherhub.user.UserMapper;
import com.weatherhub.rbac.Role;
import com.weatherhub.rbac.RoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectQueryToolsTest {
    @Test void reportsCurrentUsersActualRoles() {
        var users = mock(UserMapper.class);
        var roles = mock(RoleMapper.class);
        var user = new User(); user.setId(7L); user.setUsername("demo");
        var role = new Role(); role.setCode("USER"); role.setName("普通用户");
        when(users.selectById(7L)).thenReturn(user);
        when(roles.selectByUserId(7L)).thenReturn(List.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", "unused", List.of()));
        try { assertTrue(new CurrentUserTool(users, roles).execute(null).contains("普通用户(USER)")); }
        finally { SecurityContextHolder.clearContext(); }
    }
    @Test void derivesBoundsFromFeatureAndDoesNotInventUnknownCoordinates() {
        String feature = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[120,31],[121,32],[119,30],[120,31]]]}}";
        assertEquals("西=119.0 南=30.0 东=121.0 北=32.0", ListGisFeaturesTool.bounds(feature));
        assertEquals("未知", ListGisFeaturesTool.bounds("{}"));
    }
}
