package com.weatherhub.ai.rag;

import com.weatherhub.ai.llm.LlmClient;
import com.weatherhub.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class EmbeddingIndex {

    private final LlmClient llmClient;
    private final AiProperties properties;

    public EmbeddingIndex(LlmClient llmClient, AiProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public List<RagHit> search(String query, List<KnowledgeDoc> docs, int limit) {
        if (!properties.hasEmbeddingModel() || !llmClient.configured()) {
            return List.of();
        }
        List<Double> queryVector = llmClient.embed(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }
        List<RagHit> hits = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            List<Double> docVector = llmClient.embed(doc.title() + "\n" + doc.content());
            if (docVector.isEmpty()) {
                continue;
            }
            double cosine = cosine(queryVector, docVector);
            if (cosine > 0.15) {
                hits.add(new RagHit(doc.title(), doc.content(), cosine));
            }
        }
        hits.sort(Comparator.comparingDouble(RagHit::score).reversed());
        if (hits.size() > limit) {
            return hits.subList(0, limit);
        }
        return hits;
    }

    static double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        if (size == 0) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
