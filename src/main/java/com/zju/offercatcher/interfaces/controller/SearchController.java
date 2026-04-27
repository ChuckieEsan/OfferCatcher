package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.service.RetrievalApplicationService;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.SearchDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final RetrievalApplicationService retrievalService;

    public SearchController(RetrievalApplicationService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public ResponseEntity<SearchResponse> search(@UserId String userId, @Valid @RequestBody SearchRequest req) {
        int k = req.k() > 0 ? req.k() : 10;
        float threshold = req.scoreThreshold() > 0 ? req.scoreThreshold() : 0.3f;

        List<RetrievalApplicationService.SearchResult> results = retrievalService.searchWithRerank(
            userId, req.query(), req.company(), req.position(), k, 3);

        List<SearchResultItem> items = results.stream()
            .map(r -> new SearchResultItem(
                r.questionId(), r.questionText(), r.company(), r.position(),
                r.masteryLevel(), r.questionType(),
                r.coreEntities(), r.clusterIds(),
                r.questionAnswer(), r.metadata(), r.score()))
            .toList();

        return ResponseEntity.ok(new SearchResponse(items, items.size()));
    }
}
