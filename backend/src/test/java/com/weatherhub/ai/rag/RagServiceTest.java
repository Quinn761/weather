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
        assertTrue(hits.stream().anyMatch(hit -> hit.title().contains("JWT")));
    }

    @Test
    void retrievesGisKnowledge() {
        RagService rag = new RagService();
        List<RagHit> hits = rag.retrieve("PostGIS 打点圈地", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(hit -> hit.title().contains("GIS")));
    }
}
