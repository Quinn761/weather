package com.weatherhub.auth; // 声明当前 Java 文件所属的包路径

public interface TokenBlacklist { // 登录令牌黑名单，退出后拒绝继续使用

    void deny(String token); // 把令牌加入黑名单，有效期与 JWT 剩余时间一致

    boolean denied(String token); // 判断令牌是否已经退出
} //
