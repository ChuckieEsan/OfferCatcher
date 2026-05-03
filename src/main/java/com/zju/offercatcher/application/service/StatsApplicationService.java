package com.zju.offercatcher.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.infrastructure.adapters.cache.CacheAdapter;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import com.zju.offercatcher.interfaces.dto.StatsDto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StatsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StatsApplicationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int STATS_TTL = 300;

    private final QuestionJpaRepository questionJpaRepo;
    private final CacheAdapter cacheAdapter;

    public StatsApplicationService(QuestionJpaRepository questionJpaRepo,
                                   CacheAdapter cacheAdapter) {
        this.questionJpaRepo = questionJpaRepo;
        this.cacheAdapter = cacheAdapter;
    }

    public OverviewStats getOverview() {
        String json = cacheAdapter.getWithLock(CacheKeys.statsOverview(), () -> {
            List<QuestionJpaEntity> all = questionJpaRepo.findAll();
            Set<String> companies = new HashSet<>();
            Set<String> positions = new HashSet<>();
            Map<String, Integer> byType = new HashMap<>();
            Map<Integer, Integer> byMastery = new HashMap<>();
            long hasAnswer = 0, noAnswer = 0;

            for (QuestionJpaEntity q : all) {
                if (q.getCompany() != null && !q.getCompany().isBlank()) companies.add(q.getCompany());
                if (q.getPosition() != null && !q.getPosition().isBlank()) positions.add(q.getPosition());
                byType.merge(q.getQuestionType().getValue(), 1, Integer::sum);
                byMastery.merge(q.getMasteryLevel().getLevel(), 1, Integer::sum);
                if (q.getAnswer() != null && !q.getAnswer().isBlank()) {
                    hasAnswer++;
                } else {
                    noAnswer++;
                }
            }

            OverviewStats stats = new OverviewStats(all.size(), companies.size(), positions.size(),
                    byType, byMastery, hasAnswer, noAnswer);
            return toJson(stats);
        }, STATS_TTL);

        return fromJson(json, OverviewStats.class);
    }

    public List<CompanyStats> getCompanyStats() {
        String json = cacheAdapter.getWithLock(CacheKeys.statsCompanies(), () -> {
            List<QuestionJpaEntity> all = questionJpaRepo.findAll();

            Map<String, int[]> companyData = new LinkedHashMap<>();
            for (QuestionJpaEntity q : all) {
                String company = q.getCompany() != null && !q.getCompany().isBlank()
                        ? q.getCompany() : "未知";
                int[] data = companyData.computeIfAbsent(company, k -> new int[3]);
                data[0]++;
                if (q.getMasteryLevel().getLevel() >= 2) data[1]++;
                if (q.getAnswer() != null && !q.getAnswer().isBlank()) data[2]++;
            }

            List<CompanyStats> stats = companyData.entrySet().stream()
                    .map(e -> new CompanyStats(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                    .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                    .toList();
            return toJson(stats);
        }, STATS_TTL);

        return fromJsonList(json, CompanyStats.class);
    }

    public List<PositionStats> getPositionStats() {
        String json = cacheAdapter.getWithLock(CacheKeys.statsPositions(), () -> {
            List<QuestionJpaEntity> all = questionJpaRepo.findAll();

            Map<String, Integer> positionData = new LinkedHashMap<>();
            for (QuestionJpaEntity q : all) {
                String position = q.getPosition() != null && !q.getPosition().isBlank()
                        ? q.getPosition() : "未知";
                positionData.merge(position, 1, Integer::sum);
            }

            List<PositionStats> stats = positionData.entrySet().stream()
                    .map(e -> new PositionStats(e.getKey(), e.getValue()))
                    .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                    .toList();
            return toJson(stats);
        }, STATS_TTL);

        return fromJsonList(json, PositionStats.class);
    }

    public List<EntityStats> getEntityStats(String company, int limit) {
        String cacheKey = CacheKeys.statsEntities(company, limit);

        String json = cacheAdapter.getWithLock(cacheKey, () -> {
            List<QuestionJpaEntity> all = questionJpaRepo.findAll();

            Map<String, Integer> entityData = new LinkedHashMap<>();
            for (QuestionJpaEntity q : all) {
                if (company != null && !company.equals(q.getCompany())) continue;
                if (q.getCoreEntities() != null) {
                    for (String entity : q.getCoreEntities()) {
                        if (entity != null && !entity.isBlank()) {
                            entityData.merge(entity, 1, Integer::sum);
                        }
                    }
                }
            }

            List<EntityStats> stats = entityData.entrySet().stream()
                    .map(e -> new EntityStats(e.getKey(), e.getValue()))
                    .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                    .limit(limit)
                    .toList();
            return toJson(stats);
        }, STATS_TTL);

        return fromJsonList(json, EntityStats.class);
    }

    public List<ClusterStats> getClusterStats() {
        String json = cacheAdapter.getWithLock(CacheKeys.statsClusters(), () -> {
            List<QuestionJpaEntity> all = questionJpaRepo.findAll();

            Map<String, Integer> clusterData = new LinkedHashMap<>();
            for (QuestionJpaEntity q : all) {
                if (q.getClusterIds() != null) {
                    for (String clusterId : q.getClusterIds()) {
                        if (clusterId != null && !clusterId.isBlank()) {
                            clusterData.merge(clusterId, 1, Integer::sum);
                        }
                    }
                }
            }

            List<ClusterStats> stats = clusterData.entrySet().stream()
                    .map(e -> new ClusterStats(e.getKey(), e.getValue()))
                    .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                    .toList();
            return toJson(stats);
        }, STATS_TTL);

        return fromJsonList(json, ClusterStats.class);
    }

    private static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize stats", e);
        }
    }

    private static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize stats", e);
        }
    }

    private static <T> List<T> fromJsonList(String json, Class<T> elementClass) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize stats list", e);
        }
    }
}
