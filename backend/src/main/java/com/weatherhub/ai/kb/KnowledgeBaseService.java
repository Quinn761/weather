package com.weatherhub.ai.kb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weatherhub.ai.kb.dto.KnowledgeArticleVO;
import com.weatherhub.ai.kb.dto.SaveKnowledgeRequest;
import com.weatherhub.ai.rag.KnowledgeDoc;
import com.weatherhub.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeArticleMapper mapper;

    public KnowledgeBaseService(KnowledgeArticleMapper mapper) {
        this.mapper = mapper;
    }

    public List<KnowledgeArticleVO> list(String keyword) {
        LambdaQueryWrapper<KnowledgeArticle> query = new LambdaQueryWrapper<KnowledgeArticle>()
                .orderByDesc(KnowledgeArticle::getUpdatedAt);
        if (StringUtils.hasText(keyword)) {
            String needle = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(KnowledgeArticle::getTitle, needle)
                    .or()
                    .like(KnowledgeArticle::getContent, needle)
                    .or()
                    .like(KnowledgeArticle::getTags, needle));
        }
        return mapper.selectList(query).stream().map(this::toVo).toList();
    }

    public KnowledgeArticleVO get(Long id) {
        return toVo(require(id));
    }

    public KnowledgeArticleVO create(SaveKnowledgeRequest request) {
        KnowledgeArticle article = new KnowledgeArticle();
        fill(article, request);
        LocalDateTime now = LocalDateTime.now();
        article.setCreatedAt(now);
        article.setUpdatedAt(now);
        mapper.insert(article);
        return toVo(article);
    }

    public KnowledgeArticleVO update(Long id, SaveKnowledgeRequest request) {
        KnowledgeArticle article = require(id);
        fill(article, request);
        article.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(article);
        return toVo(article);
    }

    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    public List<KnowledgeDoc> enabledDocs() {
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeArticle>()
                        .eq(KnowledgeArticle::getStatus, KnowledgeArticle.STATUS_ENABLED)
                        .orderByDesc(KnowledgeArticle::getUpdatedAt))
                .stream()
                .map(item -> new KnowledgeDoc(item.getTitle(), item.getContent()))
                .toList();
    }

    private KnowledgeArticle require(Long id) {
        KnowledgeArticle article = mapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "知识条目不存在");
        }
        return article;
    }

    private void fill(KnowledgeArticle article, SaveKnowledgeRequest request) {
        article.setTitle(request.title().trim());
        article.setContent(request.content().trim());
        article.setTags(request.tags() == null ? "" : request.tags().trim());
        String status = request.status() == null || request.status().isBlank()
                ? KnowledgeArticle.STATUS_ENABLED
                : request.status().trim();
        if (!KnowledgeArticle.STATUS_ENABLED.equals(status) && !KnowledgeArticle.STATUS_DISABLED.equals(status)) {
            throw new BusinessException("状态只能是 ENABLED 或 DISABLED");
        }
        article.setStatus(status);
    }

    private KnowledgeArticleVO toVo(KnowledgeArticle article) {
        return new KnowledgeArticleVO(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getTags(),
                article.getStatus(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
