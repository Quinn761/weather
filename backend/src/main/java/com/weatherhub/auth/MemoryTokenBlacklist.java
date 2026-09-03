package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 导入 ConditionalOnMissingBean 类型或包供本文件使用
import org.springframework.data.redis.core.StringRedisTemplate; // 导入 StringRedisTemplate 类型或包供本文件使用
import org.springframework.stereotype.Component; // 导入 Component 类型或包供本文件使用

import java.time.Duration; // 导入 Duration 类型或包供本文件使用
import java.util.Map; // 导入 Map 类型或包供本文件使用
import java.util.concurrent.ConcurrentHashMap; // 导入 ConcurrentHashMap 类型或包供本文件使用

@Component // 声明这是 Spring 管理的组件
@ConditionalOnMissingBean(StringRedisTemplate.class) // 没有 Redis 时（例如单元测试）用内存黑名单
public class MemoryTokenBlacklist implements TokenBlacklist { // 声明 MemoryTokenBlacklist 类

    private final Map<String, Long> deniedUntil = new ConcurrentHashMap<>(); // 保存令牌指纹到过期时间戳
    private final JwtService jwtService; // 定义 jwtService 字段保存对象状态或依赖

    public MemoryTokenBlacklist(JwtService jwtService) { // 定义构造方法的入口
        this.jwtService = jwtService; // 写入 JWT 服务
    } //

    @Override // 应用 Override 注解配置当前声明
    public void deny(String token) { // 定义 deny 方法的入口
        if (token == null || token.isBlank()) { // 没有令牌时无需处理
            return; // 直接返回
        } //
        try { // 无效令牌直接忽略
            Duration ttl = jwtService.remainingTtl(token); // 计算剩余有效期
            if (ttl.isZero() || ttl.isNegative()) { // 已过期不必记录
                return; // 直接返回
            } //
            deniedUntil.put(RedisTokenBlacklist.fingerprint(token), System.currentTimeMillis() + ttl.toMillis()); // 写入内存黑名单
        } catch (Exception ignored) { // 解析失败时当作未登录退出
            // 退出接口仍返回成功
        } //
    } //

    @Override // 应用 Override 注解配置当前声明
    public boolean denied(String token) { // 定义 denied 方法的入口
        if (token == null || token.isBlank()) { // 空令牌视为未拉黑
            return false; // 返回未拉黑
        } //
        String key = RedisTokenBlacklist.fingerprint(token); // 计算令牌指纹
        Long expireAt = deniedUntil.get(key); // 读取过期时间
        if (expireAt == null) { // 不在黑名单里
            return false; // 返回未拉黑
        } //
        if (expireAt < System.currentTimeMillis()) { // 已到自然过期时间
            deniedUntil.remove(key); // 清掉过期记录
            return false; // 返回未拉黑
        } //
        return true; // 仍在黑名单有效期内
    } //
} //
