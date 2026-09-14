package com.weatherhub.ai.rag;

import com.weatherhub.ai.kb.KnowledgeBaseService;
import com.weatherhub.config.AiProperties;
import com.weatherhub.gis.GisFeatureService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RagService {

    private final AiProperties aiProperties;
    private final EmbeddingIndex embeddingIndex;
    private final KnowledgeBaseService knowledgeBase;

    @org.springframework.beans.factory.annotation.Autowired
    public RagService(
            ObjectProvider<GisFeatureService> gis,
            ObjectProvider<AiProperties> properties,
            ObjectProvider<EmbeddingIndex> embeddings,
            ObjectProvider<KnowledgeBaseService> knowledge
    ) {
        this.aiProperties = properties.getIfAvailable();
        this.embeddingIndex = embeddings.getIfAvailable();
        this.knowledgeBase = knowledge.getIfAvailable();
    }

    RagService() {
        this.aiProperties = null;
        this.embeddingIndex = null;
        this.knowledgeBase = null;
    }

    public int documentCount() {
        return corpus().size();
    }

    public List<RagHit> retrieve(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<KnowledgeDoc> docs = corpus();
        if (embeddingIndex != null && aiProperties != null && aiProperties.hasEmbeddingModel()) {
            try {
                List<RagHit> vectorHits = embeddingIndex.search(query, docs, limit);
                if (!vectorHits.isEmpty()) return vectorHits;
            } catch (Exception ignored) {
                // A vector provider outage must not hide local project documentation.
            }
        }
        return keywordSearch(query, docs, limit);
    }

    List<RagHit> keywordSearch(String query, List<KnowledgeDoc> docs, int limit) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return docs.stream()
                .map(doc -> new RagHit(doc.title(), doc.content(), score(needle, doc)))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(RagHit::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<KnowledgeDoc> corpus() {
        List<KnowledgeDoc> docs = new ArrayList<>(ProjectKnowledge.documents());
        if (knowledgeBase != null && com.weatherhub.ai.tool.ToolAccess.allowed("kb:read")) {
            try {
                knowledgeBase.enabledDocs().forEach(doc -> docs.add(new KnowledgeDoc(
                        "知识库文章：" + doc.title(), doc.content())));
            } catch (Exception ignored) {
                // Keep the versioned project documentation available when the database is unavailable.
            }
        }
        return docs;
    }

    static double score(String query, KnowledgeDoc doc) {
        String haystack = (doc.title() + "\n" + doc.content()).toLowerCase(Locale.ROOT);
        double value = 0;
        if (haystack.contains(query)) {
            value += 5;
        }
        for (String token : query.split("[\\s,，。？?、]+")) {
            if (token.length() >= 2 && haystack.contains(token)) {
                value += 2;
            }
        }
        for (int i = 0; i < query.length() - 1; i++) {
            String gram = query.substring(i, i + 2);
            if (haystack.contains(gram)) {
                value += 0.5;
            }
        }
        return value;
    }
}
