package com.weatherhub.ai.tool;

public record ToolAuthorizationDecision(boolean allowed, String reason) {
    public static ToolAuthorizationDecision allow() { return new ToolAuthorizationDecision(true, "权限策略已允许"); }
    public static ToolAuthorizationDecision deny(String reason) { return new ToolAuthorizationDecision(false, reason); }
}
