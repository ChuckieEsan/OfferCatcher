package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.question.valueobjects.QuestionWithScore;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.adapters.reranker.OnnxRerankerAdapter;
import com.zju.offercatcher.infrastructure.adapters.reranker.RankedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 检索应用服务
 *
 * 两阶段检索：向量召回 + Rerank 精排。
 * 对应 Python: app/application/services/retrieval_service.py
 */
@Service
public class RetrievalApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalApplicationService.class);

    private final QuestionRepository questionRepository;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final OnnxRerankerAdapter rerankerAdapter;

    public RetrievalApplicationService(QuestionRepository questionRepository,
                                        OnnxEmbeddingAdapter embeddingAdapter,
                                        OnnxRerankerAdapter rerankerAdapter) {
        this.questionRepository = questionRepository;
        this.embeddingAdapter = embeddingAdapter;
        this.rerankerAdapter = rerankerAdapter;
    }

    /**
     * 基础向量检索（用户隔离）
     */
    public List<SearchResult> search(String userId, String query, String company, String position,
                                      int k, float scoreThreshold,
                                      Integer masteryLevel, String questionType,
                                      List<String> coreEntities, List<String> clusterIds) {
        String context = buildQueryContext(query, company, position);
        float[] queryVector = embeddingAdapter.embed(context);

        Map<String, Object> filters = buildFilters(company, position, masteryLevel, questionType,
            coreEntities, clusterIds);

        List<QuestionWithScore> results = questionRepository.searchUserVisible(
            userId, queryVector, filters, k);

        return results.stream()
            .filter(r -> r.score() >= scoreThreshold)
            .map(r -> toSearchResult(r.question(), r.score()))
            .toList();
    }

    /**
     * 两阶段检索：向量召回（k * recallMultiplier） + Rerank 精排（top k）
     */
    public List<SearchResult> searchWithRerank(String userId, String query, String company,
                                                String position, int k, int recallMultiplier) {
        return searchWithRerank(userId, query, company, position, k, recallMultiplier,
            null, null, null, null);
    }

    public List<SearchResult> searchWithRerank(String userId, String query, String company,
                                                String position, int k, int recallMultiplier,
                                                Integer masteryLevel, String questionType,
                                                List<String> coreEntities, List<String> clusterIds) {
        String context = buildQueryContext(query, company, position);
        float[] queryVector = embeddingAdapter.embed(context);

        Map<String, Object> filters = buildFilters(company, position, masteryLevel, questionType,
            coreEntities, clusterIds);

        int recallLimit = k * recallMultiplier;
        List<QuestionWithScore> recalled = questionRepository.searchUserVisible(
            userId, queryVector, filters, recallLimit);

        if (recalled.isEmpty()) {
            return Collections.emptyList();
        }

        List<Question> candidates = recalled.stream()
            .map(QuestionWithScore::question)
            .toList();
        List<String> candidateTexts = candidates.stream()
            .map(Question::getQuestionText)
            .toList();

        if (rerankerAdapter.isInitialized()) {
            List<RankedResult> ranked = rerankerAdapter.rerank(query, candidateTexts, k);
            List<SearchResult> results = new ArrayList<>();
            for (RankedResult r : ranked) {
                Question question = candidates.get(r.originalIndex());
                results.add(toSearchResult(question, r.score()));
            }
            log.info("Search with rerank: query='{}', recall={}, rerankTop={}",
                query, candidates.size(), results.size());
            return results;
        }

        // Fallback: return top-k by vector similarity
        return recalled.stream()
            .limit(k)
            .map(r -> toSearchResult(r.question(), r.score()))
            .toList();
    }

    // ==================== Helper Methods ====================

    static String buildQueryContext(String query, String company, String position) {
        String c = company != null && !company.isBlank() ? company : "综合";
        String p = position != null && !position.isBlank() ? position : "综合";
        return "公司：" + c + " | 岗位：" + p + " | 题目：" + query;
    }

    static Map<String, Object> buildFilters(String company, String position,
                                             Integer masteryLevel, String questionType,
                                             List<String> coreEntities, List<String> clusterIds) {
        Map<String, Object> filters = new HashMap<>();
        if (company != null && !company.isBlank()) filters.put("company", company);
        if (position != null && !position.isBlank()) filters.put("position", position);
        if (masteryLevel != null) filters.put("masteryLevel", masteryLevel);
        if (questionType != null && !questionType.isBlank()) filters.put("questionType", questionType);
        if (coreEntities != null && !coreEntities.isEmpty()) filters.put("coreEntities", coreEntities);
        if (clusterIds != null && !clusterIds.isEmpty()) filters.put("clusterIds", clusterIds);
        return filters;
    }

    private static SearchResult toSearchResult(Question q, float score) {
        return new SearchResult(
            q.getQuestionHash(), q.getQuestionText(), q.getCompany(), q.getPosition(),
            q.getMasteryLevel() != null ? q.getMasteryLevel().name() : null,
            q.getQuestionType() != null ? q.getQuestionType().name() : null,
            q.getCoreEntities(), q.getClusterIds(), q.getAnswer(), q.getMetadata(), score
        );
    }

    /**
     * 检索结果 DTO
     */
    public record SearchResult(
        String questionId, String questionText, String company, String position,
        String masteryLevel, String questionType,
        List<String> coreEntities, List<String> clusterIds,
        String questionAnswer, Map<String, Object> metadata, float score
    ) {}
}
