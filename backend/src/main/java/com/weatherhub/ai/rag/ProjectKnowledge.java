package com.weatherhub.ai.rag;

import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class ProjectKnowledge {
    private static final String CONTEXT = read();
    private ProjectKnowledge() {}
    public static String context() { return CONTEXT; }
    public static List<KnowledgeDoc> documents() {
        return Arrays.stream(CONTEXT.split("(?m)^## ")).skip(1).map(section -> {
            int end = section.indexOf('\n');
            return new KnowledgeDoc("项目源码：" + section.substring(0, end).trim(), section.substring(end).trim());
        }).toList();
    }
    private static String read() {
        try {
            return new ClassPathResource("ai/project-knowledge.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("项目知识资源缺失", ex);
        }
    }
}
