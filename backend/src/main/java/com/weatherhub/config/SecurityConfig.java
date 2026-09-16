package com.weatherhub.config; // 声明当前 Java 文件所属的包路径

import com.weatherhub.auth.JwtAuthFilter; // 导入 JwtAuthFilter 类型或包供本文件使用
import com.weatherhub.common.ApiResponse; // 导入 ApiResponse 类型或包供本文件使用
import jakarta.servlet.DispatcherType; // 导入 DispatcherType 类型或包供本文件使用
import jakarta.servlet.http.HttpServletResponse; // 导入 HttpServletResponse 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.boot.web.servlet.FilterRegistrationBean; // 导入 FilterRegistrationBean 类型或包供本文件使用
import org.springframework.context.annotation.Bean; // 导入 Bean 类型或包供本文件使用
import org.springframework.context.annotation.Configuration; // 导入 Configuration 类型或包供本文件使用
import org.springframework.http.HttpMethod; // 导入 HttpMethod 类型或包供本文件使用
import org.springframework.http.MediaType; // 导入 MediaType 类型或包供本文件使用
import org.springframework.security.config.Customizer; // 导入 Customizer 类型或包供本文件使用
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // 导入 HttpSecurity 类型或包供本文件使用
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // 导入 AbstractHttpConfigurer 类型或包供本文件使用
import org.springframework.security.config.http.SessionCreationPolicy; // 导入 SessionCreationPolicy 类型或包供本文件使用
import org.springframework.security.core.userdetails.UserDetailsService; // 导入 UserDetailsService 类型或包供本文件使用
import org.springframework.security.core.userdetails.UsernameNotFoundException; // 导入 UsernameNotFoundException 类型或包供本文件使用
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // 导入 BCryptPasswordEncoder 类型或包供本文件使用
import org.springframework.security.crypto.password.PasswordEncoder; // 导入 PasswordEncoder 类型或包供本文件使用
import org.springframework.security.web.SecurityFilterChain; // 导入 SecurityFilterChain 类型或包供本文件使用
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // 导入 UsernamePasswordAuthenticationFilter 类型或包供本文件使用
import tools.jackson.databind.json.JsonMapper; // 导入 JsonMapper 类型或包供本文件使用

import java.nio.charset.StandardCharsets; // 导入 StandardCharsets 类型或包供本文件使用

@Configuration // 声明这是 Spring 配置类
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class SecurityConfig { // 声明 SecurityConfig 类

    private final JwtAuthFilter jwtAuthFilter; // 定义 jwtAuthFilter 字段保存对象状态或依赖
    private final JsonMapper jsonMapper = JsonMapper.builder().build(); // 用于写出 401/403 的统一 JSON

    @Bean // 声明该方法返回值注册为 Spring Bean
    public PasswordEncoder passwordEncoder() { // 定义 passwordEncoder 方法的入口
        return new BCryptPasswordEncoder(); // 返回 BCrypt 密码编码器
    } // 

    @Bean // 声明该方法返回值注册为 Spring Bean
    public UserDetailsService userDetailsService() { // 关闭默认内存用户，登录只走 JWT
        return username -> { // 任何用户名都视为不存在
            throw new UsernameNotFoundException(username); // 抛出未找到用户异常
        }; // 结束当前多行语句
    } // 

    @Bean // 声明该方法返回值注册为 Spring Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) { // 避免过滤器被 Servlet 容器再注册一次
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter); // 包装 JWT 过滤器
        registration.setEnabled(false); // 只让 Spring Security 链调用它
        return registration; // 返回禁用后的注册信息
    } // 

    @Bean // 声明该方法返回值注册为 Spring Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { // 定义 securityFilterChain 方法的入口
        http.csrf(AbstractHttpConfigurer::disable) // 关闭 CSRF，接口改用 JWT
                .cors(Customizer.withDefaults()) // 启用 CORS 配置源
                .httpBasic(AbstractHttpConfigurer::disable) // 关闭浏览器 Basic 弹窗
                .formLogin(AbstractHttpConfigurer::disable) // 关闭默认表单登录页
                .securityMatcher("/api/**") // 只保护接口，静态页交给 Nginx
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 使用无状态会话
                .authorizeHttpRequests(auth -> auth // 开始配置接口访问规则
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll() // SSE 完成后的异步派发不再二次鉴权
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 放行浏览器预检请求
                        .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/health").permitAll() // 登录、退出和健康检查无需令牌
                        .requestMatchers(HttpMethod.GET, "/api/users/**").hasAuthority("user:read") // 查询用户需要读权限
                        .requestMatchers("/api/users/**").hasAuthority("user:write") // 改用户需要写权限
                        .requestMatchers(HttpMethod.GET, "/api/roles/**").hasAuthority("role:read") // 查询角色需要读权限
                        .requestMatchers("/api/roles/**").hasAuthority("role:write") // 改角色需要写权限
                        .requestMatchers(HttpMethod.GET, "/api/menus/**").hasAuthority("menu:read") // 查询菜单需要读权限
                        .requestMatchers("/api/menus/**").hasAuthority("menu:write") // 改菜单需要写权限
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAuthority("dashboard:view") // 工作台需要查看权限
                        .requestMatchers(HttpMethod.GET, "/api/gis/**").hasAuthority("gis:read") // 查询 GIS 标注需要读权限
                        .requestMatchers("/api/gis/**").hasAuthority("gis:write") // 改 GIS 标注需要写权限
                        .requestMatchers(HttpMethod.GET, "/api/cameras/**").hasAuthority("camera:read")
                        .requestMatchers("/api/cameras/**").hasAuthority("camera:write")
                        .requestMatchers(HttpMethod.GET, "/api/kb/**").hasAuthority("kb:read")
                        .requestMatchers("/api/kb/**").hasAuthority("kb:write")
                        .requestMatchers("/api/ai/**").hasAuthority("ai:chat") // 大模型助手需要对话权限
                        .anyRequest().authenticated()) // 其余接口只要登录即可
                .exceptionHandling(handling -> handling // 开始配置未登录和无权限的返回体
                        .authenticationEntryPoint((request, response, ex) -> writeJson(response, 401, "未登录或登录已过期")) // 未登录返回 401
                        .accessDeniedHandler((request, response, ex) -> writeJson(response, 403, "没有访问权限"))) // 无权限返回 403
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // 在默认认证过滤器前解析 JWT
        return http.build(); // 返回组装好的安全过滤链
    } // 

    private void writeJson(HttpServletResponse response, int status, String message) throws java.io.IOException { // 定义 writeJson 方法的入口
        response.setStatus(status); // 写入 HTTP 状态码
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); // 设置响应字符集
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // 设置 JSON 响应类型
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(status, message)); // 写出统一错误结构
    } // 
} // 
