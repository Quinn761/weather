package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import com.weatherhub.config.JwtProperties; // 导入 JwtProperties 类型或包供本文件使用
import io.jsonwebtoken.Claims; // 导入 Claims 类型或包供本文件使用
import io.jsonwebtoken.Jwts; // 导入 Jwts 类型或包供本文件使用
import io.jsonwebtoken.security.Keys; // 导入 Keys 类型或包供本文件使用
import org.springframework.stereotype.Component; // 导入 Component 类型或包供本文件使用

import javax.crypto.SecretKey; // 导入 SecretKey 类型或包供本文件使用
import java.nio.charset.StandardCharsets; // 导入 StandardCharsets 类型或包供本文件使用
import java.time.Instant; // 导入 Instant 类型或包供本文件使用
import java.util.Date; // 导入 Date 类型或包供本文件使用

@Component // 应用 Component 注解配置当前声明
public class JwtService { // 声明 JwtService 类

    private final JwtProperties jwtProperties; // 定义 jwtProperties 字段保存对象状态或依赖

    public JwtService(JwtProperties jwtProperties) { // 定义构造方法的入口
        this.jwtProperties = jwtProperties; // 执行赋值语句完成当前步骤
        String secret = jwtProperties.getSecret(); // 读取签名密钥
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) { // HS256 至少需要 32 字节
            throw new IllegalStateException("JWT_SECRET 至少需要 32 个字符，请在生产环境更换默认值"); // 密钥过短时直接拒绝启动
        } // 结束当前代码块
    } // 

    public String createToken(Long userId, String username) { // 定义 createToken 方法的入口
        Instant now = Instant.now(); // 计算并保存 now 的值
        Instant expireAt = now.plusSeconds(jwtProperties.getExpireHours() * 3600); // 计算并保存 expireAt 的值
        return Jwts.builder() // 返回当前方法的处理结果
                .subject(String.valueOf(userId)) // 继续链式调用 subject 处理上一步结果
                .claim("username", username) // 继续链式调用 claim 处理上一步结果
                .issuedAt(Date.from(now)) // 继续链式调用 issuedAt 处理上一步结果
                .expiration(Date.from(expireAt)) // 继续链式调用 expiration 处理上一步结果
                .signWith(signingKey()) // 继续链式调用 signWith 处理上一步结果
                .compact(); // 继续链式调用 compact 处理上一步结果
    } // 

    public Claims parseToken(String token) { // 定义 parseToken 方法的入口
        return Jwts.parser() // 返回当前方法的处理结果
                .verifyWith(signingKey()) // 继续链式调用 verifyWith 处理上一步结果
                .build() // 继续链式调用 build 处理上一步结果
                .parseSignedClaims(token) // 继续链式调用 parseSignedClaims 处理上一步结果
                .getPayload(); // 继续链式调用 getPayload 处理上一步结果
    } // 

    public long expireSeconds() { // 定义 expireSeconds 方法的入口
        return jwtProperties.getExpireHours() * 3600; // 返回当前方法的处理结果
    } // 

    private SecretKey signingKey() { // 定义 signingKey 方法的入口
        byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8); // 计算并保存 bytes 的值
        return Keys.hmacShaKeyFor(bytes); // 返回当前方法的处理结果
    } // 
} // 
