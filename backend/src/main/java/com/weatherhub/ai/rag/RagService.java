package com.weatherhub.ai.rag;

import com.weatherhub.ai.kb.KnowledgeBaseService;
import com.weatherhub.config.AiProperties;
import com.weatherhub.gis.GisFeatureService;
import com.weatherhub.gis.dto.GisFeatureVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RagService {

    static final List<KnowledgeDoc> SEED = List.of(
            new KnowledgeDoc("RBAC 权限模型",
                    "用户-角色-菜单多对多。表：sys_user、sys_role、sys_menu、sys_user_role、sys_role_menu。"
                            + "权限编码写在菜单 permission_code，例如 user:read、gis:write。"
                            + "JwtAuthFilter 调用 MenuMapper.selectCodesByUserId，转成 SimpleGrantedAuthority。"
                            + "SecurityConfig 用 hasAuthority 控接口，不用 hasRole。侧栏只展示 TYPE_MENU/DIR。"),
            new KnowledgeDoc("JWT 与退出黑名单",
                    "登录 AuthService 校验 BCrypt 后由 JwtService 签发 HS256，subject 是 userId，默认 24 小时。"
                            + "POST /api/auth/logout 把 SHA-256(token) 写入 Redis 键 auth:deny:，TTL 等于剩余有效期。"
                            + "JwtAuthFilter 发现黑名单则不写入 SecurityContext。Redis 故障时黑名单 fail-open。"),
            new KnowledgeDoc("GIS 标注",
                    "独立 PostGIS 库 weatherhub_gis，表 gis_feature。"
                            + "写入：ST_SetSRID(ST_Force3D(ST_GeomFromGeoJSON(...)), 4326)，GiST 索引。"
                            + "前端 Cesium 打点/圈地。读接口要 gis:read，写要 gis:write。"
                            + "问当前有哪些标注时必须调用 list_gis_features，不要编造。"),
            new KnowledgeDoc("双数据源",
                    "GisDataSourceConfig 两个 Hikari 池：weatherhub-mysql 标 @Primary，weatherhub-postgis 独立。"
                            + "GIS 事务必须 @Transactional(\"gisTransactionManager\")。"
                            + "com.weatherhub.gis 的 Mapper 绑定 gisSqlSessionFactory。"),
            new KnowledgeDoc("AI 助手链路",
                    "调用顺序：Prompt 组装系统提示词 → RAG 检索知识库 → 调大模型 API → 若返回 tool_calls 则走 Function Calling"
                            + " → 工具来自 MCP 工具目录（与 HTTP JSON-RPC /api/ai/mcp 同一套）→ Agent 循环直到给出最终回答。"
                            + "兼容 OpenAI 协议，默认对接 DeepSeek，也可改接通义 compatible-mode。")
    );

    private final ObjectProvider<GisFeatureService> gisFeatures;
    private final AiProperties aiProperties;
    private final EmbeddingIndex embeddingIndex;
    private final KnowledgeBaseService knowledgeBase;

    public RagService(
            ObjectProvider<GisFeatureService> gis,
            ObjectProvider<AiProperties> properties,
            ObjectProvider<EmbeddingIndex> embeddings,
            ObjectProvider<KnowledgeBaseService> knowledge
    ) {
        this.gisFeatures = gis;
        this.aiProperties = properties.getIfAvailable();
        this.embeddingIndex = embeddings.getIfAvailable();
        this.knowledgeBase = knowledge.getIfAvailable();
    }

    RagService() {
        this.gisFeatures = null;
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
            List<RagHit> vectorHits = embeddingIndex.search(query, docs, limit);
            if (!vectorHits.isEmpty()) {
                return vectorHits;
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
        List<KnowledgeDoc> docs = new ArrayList<>();
        if (knowledgeBase != null) {
            try {
                docs.addAll(knowledgeBase.enabledDocs());
            } catch (Exception ignored) {
                // 表未就绪时退回内置种子
            }
        }
        if (docs.isEmpty()) {
            docs.addAll(SEED);
        }
        GisFeatureService gisFeatureService = gisFeatures == null ? null : gisFeatures.getIfAvailable();
        if (gisFeatureService == null) {
            return docs;
        }
        try {
            List<GisFeatureVO> features = gisFeatureService.list();
            if (features.isEmpty()) {
                docs.add(new KnowledgeDoc("当前 GIS 标注", "目前没有标注。用户可在 GIS 页打点或圈地。"));
                return docs;
            }
            StringBuilder body = new StringBuilder("当前库中的标注如下，详细坐标请再调 list_gis_features：\n");
            for (GisFeatureVO feature : features) {
                body.append("- id=").append(feature.id())
                        .append(" 名称=").append(feature.name())
                        .append(" 类型=").append(feature.type())
                        .append('\n');
            }
            docs.add(new KnowledgeDoc("当前 GIS 标注", body.toString()));
        } catch (Exception ignored) {
            docs.add(new KnowledgeDoc("当前 GIS 标注", "读取 PostGIS 失败，请检查 GIS 数据源。"));
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
