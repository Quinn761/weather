package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 导入 LambdaQueryWrapper 类型或包供本文件使用
import com.weatherhub.auth.dto.LoginRequest; // 导入 LoginRequest 类型或包供本文件使用
import com.weatherhub.auth.dto.LoginVO; // 导入 LoginVO 类型或包供本文件使用
import com.weatherhub.common.BusinessException; // 导入 BusinessException 类型或包供本文件使用
import com.weatherhub.rbac.MenuService; // 导入 MenuService 类型或包供本文件使用
import com.weatherhub.rbac.Role; // 导入 Role 类型或包供本文件使用
import com.weatherhub.rbac.RoleMapper; // 导入 RoleMapper 类型或包供本文件使用
import com.weatherhub.rbac.dto.MenuVO; // 导入 MenuVO 类型或包供本文件使用
import com.weatherhub.user.User; // 导入 User 类型或包供本文件使用
import com.weatherhub.user.UserMapper; // 导入 UserMapper 类型或包供本文件使用
import com.weatherhub.user.UserService; // 导入 UserService 类型或包供本文件使用
import com.weatherhub.user.dto.UserVO; // 导入 UserVO 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.security.crypto.password.PasswordEncoder; // 导入 PasswordEncoder 类型或包供本文件使用
import org.springframework.stereotype.Service; // 导入 Service 类型或包供本文件使用

import java.util.List; // 导入 java.util.List 类型或包供本文件使用

@Service // 声明这是 Spring 管理的业务服务组件
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class AuthService { // 声明 AuthService 类

    private final UserMapper userMapper; // 定义 userMapper 字段保存对象状态或依赖
    private final RoleMapper roleMapper; // 定义 roleMapper 字段保存对象状态或依赖
    private final MenuService menuService; // 定义 menuService 字段保存对象状态或依赖
    private final PasswordEncoder passwordEncoder; // 定义 passwordEncoder 字段保存对象状态或依赖
    private final JwtService jwtService; // 定义 jwtService 字段保存对象状态或依赖

    public LoginVO login(LoginRequest request) { // 定义 login 方法的入口
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username().trim())); // 按用户名查找账号
        String passwordHash = user == null ? null : user.getPassword(); // 取出数据库中的密码哈希
        if (user == null || passwordHash == null || passwordHash.isBlank() || !passwordEncoder.matches(request.password(), passwordHash)) { // 用户不存在或密码不匹配
            throw new BusinessException(401, "用户名或密码错误"); // 抛出异常中断当前流程并返回错误
        } // 
        if (!UserService.STATUS_ENABLED.equals(user.getStatus())) { // 判断条件是否成立以决定是否进入分支
            throw new BusinessException(403, "账号已停用"); // 抛出异常中断当前流程并返回错误
        } // 
        String token = jwtService.createToken(user.getId(), user.getUsername()); // 计算并保存 token 的值
        return new LoginVO(token, "Bearer", jwtService.expireSeconds(), toUserVO(user, true)); // 登录时附带侧栏菜单
    } // 

    public UserVO currentUser(Long userId) { // 定义 currentUser 方法的入口
        User user = userMapper.selectById(userId); // 计算并保存 user 的值
        if (user == null) { // 判断条件是否成立以决定是否进入分支
            throw new BusinessException(401, "登录已失效"); // 抛出异常中断当前流程并返回错误
        } // 
        return toUserVO(user, true); // 刷新当前用户时同样带上菜单
    } // 

    public UserVO toUserVO(User user) { // 定义 toUserVO 方法的入口
        return toUserVO(user, false); // 列表场景不查菜单树，减少查询
    } // 

    public UserVO toUserVO(User user, boolean withMenus) { // 定义带菜单开关的组装方法
        List<Role> roleEntities = roleMapper.selectByUserId(user.getId()); // 查询用户已绑定角色
        List<Long> roleIds = roleEntities.stream().map(Role::getId).toList(); // 取出角色主键
        List<String> roles = roleEntities.stream().map(Role::getCode).toList(); // 取出角色编码
        List<String> permissions = menuService.permissionCodesOf(user.getId()); // 从角色菜单汇总权限
        List<MenuVO> menus = withMenus ? menuService.sidebarOf(user.getId()) : List.of(); // 需要时再组装侧栏树
        return UserVO.from(user, roleIds, roles, permissions, menus); // 返回给前端的用户信息
    } // 
} // 
