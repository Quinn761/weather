package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 导入 LambdaQueryWrapper 类型或包供本文件使用
import com.weatherhub.rbac.Menu; // 导入 Menu 类型或包供本文件使用
import com.weatherhub.rbac.MenuMapper; // 导入 MenuMapper 类型或包供本文件使用
import com.weatherhub.rbac.Role; // 导入 Role 类型或包供本文件使用
import com.weatherhub.rbac.RoleMapper; // 导入 RoleMapper 类型或包供本文件使用
import com.weatherhub.rbac.RoleMenuMapper; // 导入 RoleMenuMapper 类型或包供本文件使用
import com.weatherhub.rbac.UserRoleMapper; // 导入 UserRoleMapper 类型或包供本文件使用
import com.weatherhub.user.User; // 导入 User 类型或包供本文件使用
import com.weatherhub.user.UserMapper; // 导入 UserMapper 类型或包供本文件使用
import com.weatherhub.user.UserService; // 导入 UserService 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import lombok.extern.slf4j.Slf4j; // 导入 Slf4j 类型或包供本文件使用
import org.springframework.boot.ApplicationArguments; // 导入 ApplicationArguments 类型或包供本文件使用
import org.springframework.boot.ApplicationRunner; // 导入 ApplicationRunner 类型或包供本文件使用
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 导入 ConditionalOnProperty 类型或包供本文件使用
import org.springframework.security.crypto.password.PasswordEncoder; // 导入 PasswordEncoder 类型或包供本文件使用
import org.springframework.stereotype.Component; // 导入 Component 类型或包供本文件使用

import java.time.LocalDateTime;

@Slf4j // 让 Lombok 注入日志对象
@Component // 声明这是 Spring 管理的组件
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
@ConditionalOnProperty(name = "weatherhub.seed-rbac", havingValue = "true") // 仅在开启种子数据时执行
public class AuthDataInitializer implements ApplicationRunner { // 声明 AuthDataInitializer 类

    private final UserMapper userMapper; // 定义 userMapper 字段保存对象状态或依赖
    private final RoleMapper roleMapper; // 定义 roleMapper 字段保存对象状态或依赖
    private final UserRoleMapper userRoleMapper; // 定义 userRoleMapper 字段保存对象状态或依赖
    private final MenuMapper menuMapper; // 定义 menuMapper 字段保存对象状态或依赖
    private final RoleMenuMapper roleMenuMapper; // 定义 roleMenuMapper 字段保存对象状态或依赖
    private final PasswordEncoder passwordEncoder; // 定义 passwordEncoder 字段保存对象状态或依赖

    @Override // 应用 Override 注解配置当前声明
    public void run(ApplicationArguments args) { // 定义应用启动后的回调入口
        try { // 菜单表可能尚未手工建好，失败时只记日志不阻断启动
            seedMenus(); // 确保内置菜单存在
            bindDefaultMenus(); // 给内置角色补齐默认菜单
        } catch (Exception ex) { // 捕获建表或写种子失败
            log.warn("初始化菜单失败，请先执行 db/menu.sql：{}", ex.getMessage()); // 提示需要先建表
        } // 
        ensureAccount("admin", "系统管理员", "admin123", "ADMIN"); // 确保管理员账号可用
        ensureAccount("user", "普通用户", "user123", "USER"); // 确保演示用普通账号可用
    } // 

    private void seedMenus() { // 定义 seedMenus 方法的入口
        ensureMenu("dashboard:view", 0L, "工作台", "/dashboard", "Odometer", 10, Menu.TYPE_MENU); // 工作台页面
        Long userMenuId = ensureMenu("user:read", 0L, "用户管理", "/users", "User", 20, Menu.TYPE_MENU); // 用户管理页面
        Long roleMenuId = ensureMenu("role:read", 0L, "角色管理", "/roles", "Avatar", 30, Menu.TYPE_MENU); // 角色管理页面
        Long menuMenuId = ensureMenu("menu:read", 0L, "菜单管理", "/menus", "Menu", 40, Menu.TYPE_MENU); // 菜单管理页面
        Long gisMenuId = ensureMenu("gis:read", 0L, "GIS 标注", "/gis", "Location", 50, Menu.TYPE_MENU);
        Long kbMenuId = ensureMenu("kb:read", 0L, "知识库", "/kb", "Collection", 55, Menu.TYPE_MENU);
        ensureMenu("ai:chat", 0L, "Agent 工作台", "/ai", "ChatDotRound", 60, Menu.TYPE_MENU);
        ensureMenu("user:write", userMenuId, "编辑用户", null, null, 21, Menu.TYPE_BUTTON); // 用户写权限
        ensureMenu("role:write", roleMenuId, "编辑角色", null, null, 31, Menu.TYPE_BUTTON); // 角色写权限
        ensureMenu("menu:write", menuMenuId, "编辑菜单", null, null, 41, Menu.TYPE_BUTTON); // 菜单写权限
        ensureMenu("gis:write", gisMenuId, "编辑 GIS 标注", null, null, 51, Menu.TYPE_BUTTON);
        ensureMenu("kb:write", kbMenuId, "编辑知识库", null, null, 56, Menu.TYPE_BUTTON);
    } // 

    private Long ensureMenu(String permissionCode, Long parentId, String name, String path, String icon, int sortNo, String type) { // 定义 ensureMenu 方法的入口
        Menu existing = menuMapper.selectOne(new LambdaQueryWrapper<Menu>().eq(Menu::getPermissionCode, permissionCode)); // 按权限编码查找菜单
        if (existing != null) {
            boolean dirty = false;
            if (name != null && !name.equals(existing.getName())) {
                existing.setName(name);
                dirty = true;
            }
            if (path != null && !path.equals(existing.getPath())) {
                existing.setPath(path);
                dirty = true;
            }
            if (dirty) {
                existing.setUpdatedAt(LocalDateTime.now());
                menuMapper.updateById(existing);
            }
            return existing.getId();
        } // 
        Menu menu = new Menu(); // 创建新的菜单对象
        menu.setParentId(parentId == null ? 0L : parentId); // 写入父级
        menu.setName(name); // 写入名称
        menu.setPath(path); // 写入路由
        menu.setIcon(icon); // 写入图标
        menu.setSortNo(sortNo); // 写入排序
        menu.setPermissionCode(permissionCode); // 写入权限编码
        menu.setType(type); // 写入类型
        menu.setStatus(Menu.STATUS_ENABLED); // 默认启用
        LocalDateTime now = LocalDateTime.now();
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        menuMapper.insert(menu); // 插入菜单记录
        log.info("已初始化菜单 {}", name); // 记录初始化成功日志
        return menu.getId(); // 返回新主键
    } // 

    private void bindDefaultMenus() { // 定义 bindDefaultMenus 方法的入口
        Role admin = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN")); // 查找管理员角色
        Role user = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "USER")); // 查找普通角色
        if (admin != null) { // 管理员存在时绑定全部菜单
            menuMapper.selectList(null).forEach(menu -> roleMenuMapper.insertIgnore(admin.getId(), menu.getId())); // 给管理员授予全部菜单
        } // 
        if (user != null) { // 普通角色存在时只绑定工作台
            Menu dashboard = menuMapper.selectOne(new LambdaQueryWrapper<Menu>().eq(Menu::getPermissionCode, "dashboard:view")); // 查找工作台菜单
            if (dashboard != null) { // 工作台菜单存在时绑定
                roleMenuMapper.insertIgnore(user.getId(), dashboard.getId()); // 普通用户只能进工作台
            } // 
        } // 
    } // 

    private void ensureAccount(String username, String nickname, String rawPassword, String roleCode) { // 定义 ensureAccount 方法的入口
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode)); // 按编码查找角色
        if (role == null) { // 判断角色是否尚未初始化
            log.warn("跳过账号 {}：角色 {} 不存在，请先执行 rbac.sql", username, roleCode); // 记录缺少角色的警告
            return; // 没有角色时不创建账号
        } // 
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username)); // 按用户名查找账号
        if (user == null) { // 判断账号是否还不存在
            user = new User(); // 创建新的用户对象
            user.setUsername(username); // 写入登录名
            user.setNickname(nickname); // 写入显示名
            user.setPassword(passwordEncoder.encode(rawPassword)); // 写入加密后的初始密码
            user.setStatus(UserService.STATUS_ENABLED); // 把账号设为启用
            userMapper.insert(user); // 插入用户记录
            log.info("已初始化登录账号 {} / {}", username, rawPassword); // 记录初始化成功日志
        } else if (user.getPassword() == null || user.getPassword().isBlank()) { // 旧数据可能还没有密码
            user.setPassword(passwordEncoder.encode(rawPassword)); // 补上加密密码
            userMapper.updateById(user); // 更新用户记录
            log.info("已为账号 {} 补齐初始密码", username); // 记录补齐密码日志
        } // 
        userRoleMapper.insertIgnore(user.getId(), role.getId()); // 给账号绑定对应角色，重复绑定会被忽略
    } // 
} // 
