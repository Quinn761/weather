package com.weatherhub.ai.kb;

import com.weatherhub.ai.kb.dto.KnowledgeArticleVO;
import com.weatherhub.ai.kb.dto.SaveKnowledgeRequest;
import com.weatherhub.ai.rag.RagHit;
import com.weatherhub.ai.rag.RagService;
import com.weatherhub.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBase;
    private final RagService ragService;

    public KnowledgeController(KnowledgeBaseService knowledgeBase, RagService ragService) {
        this.knowledgeBase = knowledgeBase;
        this.ragService = ragService;
    }

    @GetMapping("/articles")
    public ApiResponse<List<KnowledgeArticleVO>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(knowledgeBase.list(keyword));
    }

    @GetMapping("/articles/{id}")
    public ApiResponse<KnowledgeArticleVO> get(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeBase.get(id));
    }

    @PostMapping("/articles")
    public ApiResponse<KnowledgeArticleVO> create(@Valid @RequestBody SaveKnowledgeRequest request) {
        return ApiResponse.ok(knowledgeBase.create(request));
    }

    @PutMapping("/articles/{id}")
    public ApiResponse<KnowledgeArticleVO> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveKnowledgeRequest request
    ) {
        return ApiResponse.ok(knowledgeBase.update(id, request));
    }

    @DeleteMapping("/articles/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBase.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/search")
    public ApiResponse<List<RagHit>> search(@RequestParam String q) {
        return ApiResponse.ok(ragService.retrieve(q, 5));
    }
}
