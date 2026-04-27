package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.infrastructure.config.QdrantProperties;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Qdrant 向量存储服务
 *
 * 封装与 Qdrant gRPC API 的所有交互：向量上传、搜索、删除、payload 更新。
 */
@Service
public class QdrantVectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;

    public QdrantVectorStore(QdrantClient qdrantClient, QdrantProperties qdrantProperties) {
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
        log.info("QdrantVectorStore initialized: questions={}, sessionSummaries={}",
            qdrantProperties.getCollection(), qdrantProperties.getSessionSummaryCollection());
    }

    // ==================== Question 向量操作 ====================

    public List<VectorSearchHit> search(float[] queryVector, String userId, int limit) {
        Common.Filter filter = QdrantFilterBuilder.buildUserVisibleFilter(userId);
        return doSearch(qdrantProperties.getCollection(), queryVector, filter, limit);
    }

    public List<VectorSearchHit> searchPublic(float[] queryVector, int limit) {
        Common.Filter filter = Common.Filter.newBuilder()
            .addMust(io.qdrant.client.ConditionFactory.matchKeyword(
                QdrantPayloadFields.VISIBILITY, Visibility.PUBLIC.getValue()))
            .build();
        return doSearch(qdrantProperties.getCollection(), queryVector, filter, limit);
    }

    public List<VectorSearchHit> searchPrivate(String userId, float[] queryVector, int limit) {
        Common.Filter filter = QdrantFilterBuilder.buildPrivateOnlyFilter(userId);
        return doSearch(qdrantProperties.getCollection(), queryVector, filter, limit);
    }

    public void upsert(Question question, float[] embedding) {
        UUID pointId = uuidFromString(question.getQuestionId());
        Map<String, JsonWithInt.Value> payload = QdrantPayloadMapper.toPayload(question);

        Points.PointStruct point = Points.PointStruct.newBuilder()
            .setId(PointIdFactory.id(pointId))
            .setVectors(VectorsFactory.namedVectors(
                Map.of("", VectorFactory.vector(embedding))))
            .putAllPayload(payload)
            .build();

        try {
            qdrantClient.upsertAsync(qdrantProperties.getCollection(), List.of(point), null).get();
            log.debug("Upserted question vector: {}", question.getQuestionId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant upsert interrupted for question: " + question.getQuestionId(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant upsert failed for question: " + question.getQuestionId(), e.getCause());
        }
    }

    public void delete(String questionId) {
        UUID pointId = uuidFromString(questionId);
        try {
            qdrantClient.deleteAsync(
                qdrantProperties.getCollection(),
                List.of(PointIdFactory.id(pointId)),
                null
            ).get();
            log.debug("Deleted question vector: {}", questionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant delete interrupted: " + questionId, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant delete failed: " + questionId, e.getCause());
        }
    }

    public void updateVisibility(String questionId, Visibility visibility) {
        UUID pointId = uuidFromString(questionId);
        Map<String, JsonWithInt.Value> payloadUpdate = Map.of(
            QdrantPayloadFields.VISIBILITY,
            ValueFactory.value(visibility.getValue())
        );

        try {
            qdrantClient.setPayloadAsync(
                qdrantProperties.getCollection(),
                payloadUpdate,
                PointIdFactory.id(pointId),
                null, null, null
            ).get();
            log.debug("Updated visibility for question: {} to {}", questionId, visibility);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant setPayload interrupted: " + questionId, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant setPayload failed: " + questionId, e.getCause());
        }
    }

    // ==================== SessionSummary 向量操作 ====================

    public List<VectorSearchHit> searchSessionSummaries(String userId, float[] queryVector, int limit) {
        Common.Filter filter = QdrantFilterBuilder.buildUserIdFilter(userId);
        return doSearch(qdrantProperties.getSessionSummaryCollection(), queryVector, filter, limit);
    }

    public void upsertSessionSummary(SessionSummary summary) {
        Map<String, JsonWithInt.Value> payload = Map.of(
            QdrantPayloadFields.USER_ID, ValueFactory.value(summary.getUserId()),
            "conversation_id", ValueFactory.value(summary.getConversationId()),
            "summary", ValueFactory.value(truncate(summary.getSummary(), 500)),
            "importance_score", ValueFactory.value(summary.getImportanceScore()),
            "memory_layer", ValueFactory.value(summary.getMemoryLayer().name())
        );

        Points.PointStruct point = Points.PointStruct.newBuilder()
            .setId(PointIdFactory.id(summary.getId()))
            .setVectors(VectorsFactory.namedVectors(
                Map.of("", VectorFactory.vector(summary.getEmbedding()))))
            .putAllPayload(payload)
            .build();

        try {
            qdrantClient.upsertAsync(qdrantProperties.getSessionSummaryCollection(), List.of(point), null).get();
            log.debug("Upserted session summary vector: {}", summary.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant upsertSessionSummary interrupted: " + summary.getId(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant upsertSessionSummary failed: " + summary.getId(), e.getCause());
        }
    }

    public void deleteSessionSummary(Long summaryId) {
        try {
            qdrantClient.deleteAsync(
                qdrantProperties.getSessionSummaryCollection(),
                List.of(PointIdFactory.id(summaryId)),
                null
            ).get();
            log.debug("Deleted session summary vector: {}", summaryId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant deleteSessionSummary interrupted: " + summaryId, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant deleteSessionSummary failed: " + summaryId, e.getCause());
        }
    }

    // ==================== 内部方法 ====================

    private List<VectorSearchHit> doSearch(String collection, float[] queryVector,
                                           Common.Filter filter, int limit) {
        Points.SearchPoints request = Points.SearchPoints.newBuilder()
            .setCollectionName(collection)
            .addAllVector(floatList(queryVector))
            .setFilter(filter)
            .setLimit(limit)
            .setWithPayload(Points.WithPayloadSelector.newBuilder()
                .setEnable(true)
                .build())
            .build();

        try {
            List<Points.ScoredPoint> results = qdrantClient.searchAsync(request, null).get();
            return results.stream()
                .map(sp -> {
                    String id = sp.getId().hasUuid()
                        ? sp.getId().getUuid().replace("-", "")
                        : sp.getId().getNum() + "";
                    return new VectorSearchHit(id, sp.getScore());
                })
                .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Qdrant search interrupted on collection {}", collection, e);
            return Collections.emptyList();
        } catch (ExecutionException e) {
            log.error("Qdrant search failed on collection {}", collection, e.getCause());
            return Collections.emptyList();
        }
    }

    private static List<Float> floatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) list.add(v);
        return list;
    }

    /**
     * 将 UUID 字符串转为 UUID 对象，兼容有无连字符格式。
     */
    private static UUID uuidFromString(String id) {
        if (id.contains("-")) {
            return UUID.fromString(id);
        }
        // 无连字符格式：8-4-4-4-12
        String dashed = id.replaceFirst(
            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
            "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
