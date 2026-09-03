package com.weatherhub.ai.tool;

import com.weatherhub.gis.GisFeatureService;
import com.weatherhub.rbac.MenuService;
import com.weatherhub.rbac.RoleService;
import com.weatherhub.user.UserMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Component
public class DashboardOverviewTool implements AiTool {

    private final UserMapper userMapper;
    private final RoleService roleService;
    private final MenuService menuService;
    private final GisFeatureService gisFeatureService;

    public DashboardOverviewTool(
            UserMapper userMapper,
            RoleService roleService,
            MenuService menuService,
            ObjectProvider<GisFeatureService> gis
    ) {
        this.userMapper = userMapper;
        this.roleService = roleService;
        this.menuService = menuService;
        this.gisFeatureService = gis.getIfAvailable();
    }

    @Override
    public String name() {
        return "get_dashboard_overview";
    }

    @Override
    public String description() {
        return "查询系统用户数、角色数、菜单数和 GIS 标注数。问规模、有多少用户时必须调用。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(JsonNode arguments) {
        long gisCount = 0;
        String gisNote = "GIS 未启用";
        if (gisFeatureService != null) {
            try {
                gisCount = gisFeatureService.list().size();
                gisNote = String.valueOf(gisCount);
            } catch (Exception ex) {
                gisNote = "读取失败：" + ex.getMessage();
            }
        }
        return "用户 " + userMapper.selectCount(null)
                + "，角色 " + roleService.countAll()
                + "，菜单 " + menuService.countAll()
                + "，GIS 标注 " + gisNote;
    }
}
