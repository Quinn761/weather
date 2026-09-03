package com.weatherhub.ai.prompt;

import com.weatherhub.ai.rag.RagHit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptFactory {

    public static final String SYSTEM_PROMPT = """
            你是 Weather Data Hub 的后台助手。这是一个气象数据中台：Vue3 前端 + Spring Boot 后端，RBAC 权限，Cesium GIS 标注，生产跑在 K3s。

            回答规则：
            1. 用简体中文，先给结论，再补必要细节。
            2. 查实时数据必须走工具，不要编造用户数、标注列表、当前登录人。
            3. 解释系统原理时优先使用【检索资料】，不要编造表名或类名。
            4. 不知道就说不知道，并建议去对应页面（工作台 / 用户 / 角色 / 菜单 / GIS / AI 助手）。
            5. 不要输出密钥、密码、JWT 原文。
            """;

    public String withRag(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return SYSTEM_PROMPT;
        }
        String docs = hits.stream()
                .map(hit -> "《" + hit.title() + "》\n" + hit.content())
                .collect(Collectors.joining("\n\n"));
        return SYSTEM_PROMPT + "\n【检索资料】\n" + docs;
    }
}
