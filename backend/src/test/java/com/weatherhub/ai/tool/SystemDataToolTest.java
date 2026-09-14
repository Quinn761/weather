package com.weatherhub.ai.tool;

import com.weatherhub.user.UserService;
import com.weatherhub.user.dto.UserVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemDataToolTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void unauthorizedScopeDoesNotReadDatabase() {
        var users = mock(UserService.class);
        var tool = new SystemDataTool(users, null, null, null, null, null, null);
        assertTrue(tool.execute(JsonMapper.builder().build().readTree("{\"scope\":\"users\"}")).contains("user:read"));
        verifyNoInteractions(users);
    }
    @Test void returnsRolesAndPermissionsWithoutContactFieldsAndLimitsPageSize() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("1", "unused",
                List.of(new SimpleGrantedAuthority("user:read"))));
        var users = mock(UserService.class);
        var page = new Page<UserVO>(1, 20, 1);
        page.setRecords(List.of(new UserVO(2L, "alice", "Alice", "private@example.com", "123456789",
                "ENABLED", List.of(3L), List.of("USER"), List.of("ai:chat"), List.of(), null, null)));
        when(users.page(1, 20, "alice")).thenReturn(page);
        var tool = new SystemDataTool(users, null, null, null, null, null, null);
        String result = tool.execute(JsonMapper.builder().build().readTree("{\"scope\":\"users\",\"keyword\":\"alice\"}"));
        assertTrue(result.contains("ai:chat"));
        assertFalse(result.contains("private@example.com"));
        assertFalse(result.contains("123456789"));
        verify(users).page(1, 20, "alice");
    }
}
