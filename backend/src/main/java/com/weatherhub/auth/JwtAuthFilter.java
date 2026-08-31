package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import com.weatherhub.rbac.MenuMapper; // 导入 MenuMapper 类型或包供本文件使用
import com.weatherhub.user.User; // 导入 User 类型或包供本文件使用
import com.weatherhub.user.UserMapper; // 导入 UserMapper 类型或包供本文件使用
import com.weatherhub.user.UserService; // 导入 UserService 类型或包供本文件使用
import io.jsonwebtoken.JwtException; // 导入 JwtException 类型或包供本文件使用
import jakarta.servlet.FilterChain; // 导入 FilterChain 类型或包供本文件使用
import jakarta.servlet.ServletException; // 导入 ServletException 类型或包供本文件使用
import jakarta.servlet.http.HttpServletRequest; // 导入 HttpServletRequest 类型或包供本文件使用
import jakarta.servlet.http.HttpServletResponse; // 导入 HttpServletResponse 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.http.HttpHeaders; // 导入 HttpHeaders 类型或包供本文件使用
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 导入 UsernamePasswordAuthenticationToken 类型或包供本文件使用
import org.springframework.security.core.authority.SimpleGrantedAuthority; // 导入 SimpleGrantedAuthority 类型或包供本文件使用
import org.springframework.security.core.context.SecurityContextHolder; // 导入 SecurityContextHolder 类型或包供本文件使用
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource; // 导入 WebAuthenticationDetailsSource 类型或包供本文件使用
import org.springframework.stereotype.Component; // 导入 Component 类型或包供本文件使用
import org.springframework.web.filter.OncePerRequestFilter; // 导入 OncePerRequestFilter 类型或包供本文件使用

import java.io.IOException; // 导入 java.io.IOException 类型或包供本文件使用
import java.util.List; // 导入 java.util.List 类型或包供本文件使用

@Component // 声明这是 Spring 管理的组件
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class JwtAuthFilter extends OncePerRequestFilter { // 声明 JwtAuthFilter 类

    private final JwtService jwtService; // 定义 jwtService 字段保存对象状态或依赖
    private final UserMapper userMapper; // 定义 userMapper 字段保存对象状态或依赖
    private final MenuMapper menuMapper; // 定义 menuMapper 字段保存对象状态或依赖

    @Override // 应用 Override 注解配置当前声明
    protected boolean shouldNotFilter(HttpServletRequest request) { // 定义 shouldNotFilter 方法的入口
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) { // 判断是否为浏览器预检请求
            return true; // 预检请求不解析令牌
        } // 
        String path = request.getServletPath(); // 计算并保存当前请求路径
        return "/api/auth/login".equals(path) || "/api/health".equals(path); // 登录和健康检查不解析令牌
    } // 

    @Override // 应用 Override 注解配置当前声明
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) // 定义过滤器主流程入口
            throws ServletException, IOException { // 声明过滤器可能抛出的异常
        String header = request.getHeader(HttpHeaders.AUTHORIZATION); // 读取 Authorization 请求头
        if (header != null && header.startsWith("Bearer ")) { // 判断是否携带 Bearer 令牌
            String token = header.substring(7).trim(); // 截取令牌正文
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) { // 仅在尚未认证时解析令牌
                authenticate(token, request); // 用令牌恢复登录上下文
            } // 
        } // 
        filterChain.doFilter(request, response); // 继续执行后续过滤器和控制器
    } // 

    private void authenticate(String token, HttpServletRequest request) { // 定义 authenticate 方法的入口
        try { // 开始当前声明或控制结构的代码块
            String subject = jwtService.parseToken(token).getSubject(); // 从令牌中取出用户主键
            Long userId = Long.valueOf(subject); // 把主键转换成数字类型
            User user = userMapper.selectById(userId); // 按主键查询当前用户
            if (user == null || !UserService.STATUS_ENABLED.equals(user.getStatus())) { // 用户不存在或已停用则视为未登录
                return; // 不写入认证上下文
            } // 
            List<SimpleGrantedAuthority> authorities = menuMapper.selectCodesByUserId(userId).stream() // 从角色菜单汇总权限编码
                    .map(SimpleGrantedAuthority::new) // 把权限编码转成 Spring Security 的权限对象
                    .toList(); // 收集成权限列表
            UsernamePasswordAuthenticationToken authentication = // 计算并保存 authentication 的值
                    new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, authorities); // 用用户主键作为认证主体
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // 补充当前请求的认证详情
            SecurityContextHolder.getContext().setAuthentication(authentication); // 把认证结果写入安全上下文
        } catch (JwtException | IllegalArgumentException ignored) { // 令牌无效、过期或格式错误时忽略
            SecurityContextHolder.clearContext(); // 清理可能残留的认证上下文
        } // 
    } // 
} // 
