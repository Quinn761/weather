package com.weatherhub.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagServiceTest {

    @Test
    void retrievesJwtKnowledge() {
        RagService rag = new RagService();
        List<RagHit> hits = rag.retrieve("JWT 退出黑名单 Redis", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(hit -> hit.content().contains("JWT")));
    }

    @Test
    void retrievesGisKnowledge() {
        RagService rag = new RagService();
        List<RagHit> hits = rag.retrieve("PostGIS 打点圈地", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(hit -> hit.title().contains("GIS")));
    }

    @Test
    void projectFactsRemainAvailableAlongsideDatabaseArticles() {
        var knowledge = org.mockito.Mockito.mock(com.weatherhub.ai.kb.KnowledgeBaseService.class);
        org.mockito.Mockito.when(knowledge.enabledDocs()).thenReturn(List.of(new KnowledgeDoc("自定义文章", "测试正文")));
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("knowledge", knowledge);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("1", "unused", List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("kb:read"))));
        try {
            var rag = new RagService(beans.getBeanProvider(com.weatherhub.gis.GisFeatureService.class),
                    beans.getBeanProvider(com.weatherhub.config.AiProperties.class),
                    beans.getBeanProvider(EmbeddingIndex.class), beans.getBeanProvider(com.weatherhub.ai.kb.KnowledgeBaseService.class));
            assertTrue(rag.retrieve("SAM 2.1 识别地块", 3).stream().anyMatch(hit -> hit.content().contains("SAM2_CHECKPOINT")));
            assertTrue(rag.retrieve("自定义文章", 3).stream().anyMatch(hit -> hit.title().contains("自定义文章")));
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            org.mockito.Mockito.clearInvocations(knowledge);
            rag.retrieve("自定义文章", 3);
            org.mockito.Mockito.verifyNoInteractions(knowledge);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
