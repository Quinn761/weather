package com.weatherhub.user; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 导入 LambdaQueryWrapper 类型或包供本文件使用
import com.baomidou.mybatisplus.core.toolkit.StringUtils; // 导入 StringUtils 类型或包供本文件使用
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 导入 Page 类型或包供本文件使用
import com.weatherhub.auth.AuthService; // 导入 AuthService 类型或包供本文件使用
import com.weatherhub.common.BusinessException; // 导入 BusinessException 类型或包供本文件使用
import com.weatherhub.rbac.Role; // 导入 Role 类型或包供本文件使用
import com.weatherhub.rbac.RoleMapper; // 导入 RoleMapper 类型或包供本文件使用
import com.weatherhub.rbac.UserRoleMapper; // 导入 UserRoleMapper 类型或包供本文件使用
import com.weatherhub.user.dto.CreateUserRequest; // 导入 CreateUserRequest 类型或包供本文件使用
import com.weatherhub.user.dto.UpdateUserRequest; // 导入 UpdateUserRequest 类型或包供本文件使用
import com.weatherhub.user.dto.UserVO; // 导入 UserVO 类型或包供本文件使用
import org.springframework.context.annotation.Lazy; // 导入 Lazy 类型或包供本文件使用
import org.springframework.security.core.Authentication; // 导入 Authentication 类型或包供本文件使用
import org.springframework.security.core.context.SecurityContextHolder; // 导入 SecurityContextHolder 类型或包供本文件使用
import org.springframework.security.crypto.password.PasswordEncoder; // 导入 PasswordEncoder 类型或包供本文件使用
import org.springframework.stereotype.Service; // 导入 Service 类型或包供本文件使用
import org.springframework.transaction.annotation.Transactional; // 导入 Transactional 类型或包供本文件使用

@Service // 声明这是 Spring 管理的业务服务组件
public class UserService { // 声明 UserService 类

    public static final String STATUS_ENABLED = "ENABLED"; // 定义启用状态常量
    public static final String DEFAULT_ROLE_CODE = "USER"; // 新建用户默认绑定普通角色

    private final UserMapper userMapper; // 定义 userMapper 字段保存对象状态或依赖
    private final RoleMapper roleMapper; // 定义 roleMapper 字段保存对象状态或依赖
    private final UserRoleMapper userRoleMapper; // 定义 userRoleMapper 字段保存对象状态或依赖
    private final PasswordEncoder passwordEncoder; // 定义 passwordEncoder 字段保存对象状态或依赖
    private final AuthService authService; // 定义 authService 字段保存对象状态或依赖

    public UserService( // 手动构造以延迟注入 AuthService，避免和鉴权服务循环依赖
            UserMapper userMapper, // 注入用户表访问对象
            RoleMapper roleMapper, // 注入角色表访问对象
            UserRoleMapper userRoleMapper, // 注入用户角色关系访问对象
            PasswordEncoder passwordEncoder, // 注入密码编码器
            @Lazy AuthService authService // 延迟注入鉴权服务，只用于组装带角色的用户信息
    ) { // 开始当前声明或控制结构的代码块
        this.userMapper = userMapper; // 保存用户表访问对象
        this.roleMapper = roleMapper; // 保存角色表访问对象
        this.userRoleMapper = userRoleMapper; // 保存用户角色关系访问对象
        this.passwordEncoder = passwordEncoder; // 保存密码编码器
        this.authService = authService; // 保存鉴权服务
    } // 

    public Page<UserVO> page(long current, long size, String keyword) { // 定义 page 方法的入口
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt); // 按创建时间倒序查询
        if (StringUtils.isNotBlank(keyword)) { // 判断是否带了搜索关键字
            wrapper.and(w -> w.like(User::getUsername, keyword) // 按用户名模糊匹配
                    .or() // 或者按显示名匹配
                    .like(User::getNickname, keyword) // 继续链式调用 like 处理上一步结果
                    .or() // 或者按邮箱匹配
                    .like(User::getEmail, keyword)); // 继续链式调用 like 处理上一步结果
        } // 
        Page<User> result = userMapper.selectPage(new Page<>(current, size), wrapper); // 执行分页查询
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal()); // 构造返回给前端的分页对象
        voPage.setRecords(result.getRecords().stream().map(authService::toUserVO).toList()); // 附带角色和权限后返回
        return voPage; // 返回当前方法的处理结果
    } // 

    public UserVO get(Long id) { // 定义 get 方法的入口
        return authService.toUserVO(requireUser(id)); // 查询用户并附带角色权限
    } // 

    @Transactional // 让当前方法在事务中执行
    public UserVO create(CreateUserRequest request) { // 定义 create 方法的入口
        String username = request.username().trim(); // 去掉用户名两端空格
        if (existsUsername(username, null)) { // 判断用户名是否已被占用
            throw new BusinessException("用户名已存在"); // 抛出业务异常
        } // 
        User user = new User(); // 创建新的用户对象
        user.setUsername(username); // 写入登录名
        user.setNickname(request.nickname().trim()); // 写入显示名
        user.setEmail(blankToNull(request.email())); // 写入可选邮箱
        user.setPhone(blankToNull(request.phone())); // 写入可选手机号
        user.setPassword(passwordEncoder.encode(request.password())); // 把明文密码加密后存储
        user.setStatus(StringUtils.isBlank(request.status()) ? STATUS_ENABLED : request.status()); // 未传状态时默认启用
        userMapper.insert(user); // 插入用户记录
        if (request.roleIds() != null && !request.roleIds().isEmpty()) { // 创建时若指定了角色则按指定绑定
            replaceRoles(user.getId(), request.roleIds()); // 覆盖默认角色
        } else { // 开始当前声明或控制结构的代码块
            assignDefaultRole(user.getId()); // 给新用户绑定默认普通角色
        } // 
        return authService.toUserVO(user); // 返回带角色权限的用户信息
    } // 

    @Transactional // 让当前方法在事务中执行
    public UserVO update(Long id, UpdateUserRequest request) { // 定义 update 方法的入口
        User user = requireUser(id); // 确认用户存在
        user.setNickname(request.nickname().trim()); // 更新显示名
        user.setEmail(blankToNull(request.email())); // 更新邮箱
        user.setPhone(blankToNull(request.phone())); // 更新手机号
        if (StringUtils.isNotBlank(request.status())) { // 判断是否传了状态
            user.setStatus(request.status()); // 更新启用或停用状态
        } // 
        if (StringUtils.isNotBlank(request.password())) { // 判断是否要重置密码
            if (request.password().length() < 6) { // 校验新密码长度
                throw new BusinessException("密码至少 6 位"); // 抛出业务异常
            } // 
            user.setPassword(passwordEncoder.encode(request.password())); // 加密后覆盖原密码
        } // 
        userMapper.updateById(user); // 保存用户变更
        if (request.roleIds() != null) { // 编辑时若传了角色列表则覆盖
            replaceRoles(id, request.roleIds()); // 重绑角色
        } // 
        return authService.toUserVO(user); // 返回带角色权限的用户信息
    } // 

    @Transactional // 让当前方法在事务中执行
    public void delete(Long id) { // 定义 delete 方法的入口
        requireUser(id); // 确认用户存在
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 读取当前登录身份
        if (authentication != null && String.valueOf(id).equals(authentication.getName())) { // 禁止删除自己
            throw new BusinessException("不能删除当前登录账号"); // 抛出业务异常
        } // 
        userRoleMapper.deleteByUserId(id); // 先删除用户角色关系
        userMapper.deleteById(id); // 再删除用户记录
    } // 

    private User requireUser(Long id) { // 定义 requireUser 方法的入口
        User user = userMapper.selectById(id); // 按主键查询用户
        if (user == null) { // 判断用户是否不存在
            throw new BusinessException(404, "用户不存在"); // 抛出业务异常
        } // 
        return user; // 返回查到的用户
    } // 

    private void assignDefaultRole(Long userId) { // 定义 assignDefaultRole 方法的入口
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, DEFAULT_ROLE_CODE)); // 查找默认普通角色
        if (role == null) { // 判断角色种子数据是否缺失
            throw new BusinessException("默认角色不存在，请先初始化 RBAC 数据"); // 抛出业务异常
        } // 
        userRoleMapper.insertIgnore(userId, role.getId()); // 绑定默认角色
    } // 

    private void replaceRoles(Long userId, java.util.List<Long> roleIds) { // 定义 replaceRoles 方法的入口
        userRoleMapper.deleteByUserId(userId); // 先清空旧角色
        for (Long roleId : roleIds) { // 遍历新角色
            if (roleId != null && roleMapper.selectById(roleId) != null) { // 只绑定真实存在的角色
                userRoleMapper.insertIgnore(userId, roleId); // 写入用户角色关系
            } // 
        } // 
    } // 

    private boolean existsUsername(String username, Long excludeId) { // 定义 existsUsername 方法的入口
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>() // 构造用户名查重条件
                .eq(User::getUsername, username) // 按用户名精确匹配
                .ne(excludeId != null, User::getId, excludeId); // 编辑时排除当前用户自己
        return userMapper.selectCount(wrapper) > 0; // 存在同名记录则返回 true
    } // 

    private String blankToNull(String value) { // 定义 blankToNull 方法的入口
        return StringUtils.isBlank(value) ? null : value.trim(); // 空白字符串转成数据库空值
    } // 
} // 
