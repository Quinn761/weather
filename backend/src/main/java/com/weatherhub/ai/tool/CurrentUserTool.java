package com.weatherhub.ai.tool;

import com.weatherhub.user.User;
import com.weatherhub.user.UserMapper;
import com.weatherhub.rbac.RoleMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CurrentUserTool implements AiTool {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public CurrentUserTool(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public String name() {
        return "get_current_user";
    }

    @Override
    public String description() {
        return "查询当前登录用户的用户名、显示名、绑定角色名称/编码和权限编码。问我是谁、当前角色、我有什么权限时必须调用。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(JsonNode arguments) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "当前没有登录用户。";
        }
        String userId = authentication.getName();
        User user = null;
        try {
            user = userMapper.selectById(Long.valueOf(userId));
        } catch (NumberFormatException ignored) {
            // 主体不是数字主键时只返回认证信息
        }
        String permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
        if (user == null) {
            return "userId=" + userId + "，权限=[" + permissions + "]";
        }
        return "用户名=" + user.getUsername()
                + "，显示名=" + user.getNickname()
                + "，角色=" + roleMapper.selectByUserId(user.getId()).stream()
                    .map(role -> role.getName() + "(" + role.getCode() + ")").collect(Collectors.joining("、"))
                + "，状态=" + user.getStatus()
                + "，权限=[" + permissions + "]";
    }
}
