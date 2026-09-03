package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import lombok.extern.slf4j.Slf4j; // 导入 Slf4j 类型或包供本文件使用
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; // 导入 ConditionalOnBean 类型或包供本文件使用
import org.springframework.data.redis.core.StringRedisTemplate; // 导入 StringRedisTemplate 类型或包供本文件使用
import org.springframework.stereotype.Component; // 导入 Component 类型或包供本文件使用

import java.nio.charset.StandardCharsets; // 导入 StandardCharsets 类型或包供本文件使用
import java.security.MessageDigest; // 导入 MessageDigest 类型或包供本文件使用
import java.security.NoSuchAlgorithmException; // 导入 NoSuchAlgorithmException 类型或包供本文件使用
import java.time.Duration; // 导入 Duration 类型或包供本文件使用
import java.util.HexFormat; // 导入 HexFormat 类型或包供本文件使用

@Slf4j // 让 Lombok 注入日志对象
@Component // 声明这是 Spring 管理的组件
@ConditionalOnBean(StringRedisTemplate.class) // 有 Redis 时用 Redis 保存退出令牌
public class RedisTokenBlacklist implements TokenBlacklist { // 声明 RedisTokenBlacklist 类

    static final String KEY_PREFIX = "auth:deny:"; // Redis 键前缀，后面跟令牌指纹

    private final StringRedisTemplate redis; // 定义 redis 字段保存对象状态或依赖
    private final JwtService jwtService; // 定义 jwtService 字段保存对象状态或依赖

    public RedisTokenBlacklist(StringRedisTemplate redis, JwtService jwtService) { // 定义构造方法的入口
        this.redis = redis; // 写入 Redis 客户端
        this.jwtService = jwtService; // 写入 JWT 服务
    } //

    @Override // 应用 Override 注解配置当前声明
    public void deny(String token) { // 定义 deny 方法的入口
        if (token == null || token.isBlank()) { // 没有令牌时无需处理
            return; // 直接返回
        } //
        try { // 解析失败或 Redis 不可用时不打断退出
            Duration ttl = jwtService.remainingTtl(token); // 计算令牌还剩多久过期
            if (ttl.isZero() || ttl.isNegative()) { // 已经过期的令牌不必再写入
                return; // 直接返回
            } //
            redis.opsForValue().set(KEY_PREFIX + fingerprint(token), "1", ttl); // 按剩余寿命写入黑名单
        } catch (Exception ex) { // 捕获解析或 Redis 异常
            log.warn("写入 Redis 令牌黑名单失败：{}", ex.getMessage()); // 记录警告但不阻断退出
        } //
    } //

    @Override // 应用 Override 注解配置当前声明
    public boolean denied(String token) { // 定义 denied 方法的入口
        if (token == null || token.isBlank()) { // 空令牌视为未拉黑
            return false; // 返回未拉黑
        } //
        try { // Redis 故障时放行，避免把整个登录打挂
            Boolean exists = redis.hasKey(KEY_PREFIX + fingerprint(token)); // 查询黑名单键是否存在
            return Boolean.TRUE.equals(exists); // 只有明确存在才拒绝
        } catch (Exception ex) { // 捕获 Redis 异常
            log.warn("读取 Redis 令牌黑名单失败：{}", ex.getMessage()); // 记录警告
            return false; // 故障时按未退出处理
        } //
    } //

    static String fingerprint(String token) { // 把令牌做成短指纹，避免原文进 Redis
        try { // SHA-256 在 JDK 中始终可用
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // 创建摘要器
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8)); // 计算令牌哈希
            return HexFormat.of().formatHex(hash); // 转成十六进制字符串作为键
        } catch (NoSuchAlgorithmException ex) { // 理论上不会发生
            throw new IllegalStateException(ex); // 包装成运行时异常
        } //
    } //
} //
