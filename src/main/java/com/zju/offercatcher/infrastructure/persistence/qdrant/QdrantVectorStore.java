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
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    void ensureCollectionsExist() {
        ensureCollection(qdrantProperties.getCollection());
        ensureCollection(qdrantProperties.getSessionSummaryCollection());
    }

    private void ensureCollection(String collectionName) {
        try {
            boolean exists = qdrantClient.collectionExistsAsync(collectionName).get();
            if (!exists) {
                qdrantClient.createCollectionAsync(
                    collectionName,
                    io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                        .setSize(qdrantProperties.getVectorSize())
                        .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                        .build(),
                    null
                ).get();
                log.info("Created Qdrant collection: {}", collectionName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while ensuring collection: {}", collectionName);
        } catch (ExecutionException e) {
            log.error("Failed to ensure collection: {}", collectionName, e.getCause());
        }
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
        Map<String, JsonWithInt.Value> payload = QdrantPayloadMapper.toPayload(question);

        Points.PointStruct point = Points.PointStruct.newBuilder()
            .setId(PointIdFactory.id(question.getId()))
            .setVectors(VectorsFactory.namedVectors(
                Map.of("", VectorFactory.vector(embedding))))
            .putAllPayload(payload)
            .build();

        try {
            qdrantClient.upsertAsync(qdrantProperties.getCollection(), List.of(point), null).get();
            log.debug("Upserted question vector: id={}", question.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant upsert interrupted for question: " + question.getId(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant upsert failed for question: " + question.getId(), e.getCause());
        }
    }

    public void delete(Long id) {
        try {
            qdrantClient.deleteAsync(
                qdrantProperties.getCollection(),
                List.of(PointIdFactory.id(id)),
                null
            ).get();
            log.debug("Deleted question vector: id={}", id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant delete interrupted: " + id, e);
        } catch (ExecutionException e) {
            if (isCollectionNotFound(e)) {
                log.debug("Collection not found, skipping Qdrant delete: {}", id);
                return;
            }
            throw new RuntimeException("Qdrant delete failed: " + id, e.getCause());
        }
    }

    /**
     * 滚动获取 collection 中所有 point 的 ID，用于数据一致性补偿。
     */
    public Set<Long> scrollAllIds() {
        Set<Long> ids = new java.util.HashSet<>();
        Common.PointId offset = null;
        int batch = 500;
        while (true) {
            Points.ScrollPoints.Builder builder = Points.ScrollPoints.newBuilder()
                .setCollectionName(qdrantProperties.getCollection())
                .setLimit(batch)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(false));
            if (offset != null) builder.setOffset(offset);
            try {
                Points.ScrollResponse resp = qdrantClient.scrollAsync(builder.build(), null).get();
                for (Points.RetrievedPoint rp : resp.getResultList()) {
                    if (rp.getId().hasNum()) ids.add(rp.getId().getNum());
                    else if (rp.getId().hasUuid()) ids.add(Long.parseLong(rp.getId().getUuid().replace("-", "")));
                }
                if (!resp.hasNextPageOffset()) break;
                offset = resp.getNextPageOffset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Qdrant scroll interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Qdrant scroll failed", e.getCause());
            }
        }
        log.info("Scrolled {} ids from Qdrant collection {}", ids.size(), qdrantProperties.getCollection());
        return ids;
    }

    /**
     * 批量删除 Qdrant points，用于补偿脚本清理 stale entries。
     */
    public void deleteBatch(Collection<Long> ids) {
        if (ids.isEmpty()) return;
        List<Common.PointId> pointIds = ids.stream()
            .map(PointIdFactory::id)
            .toList();
        try {
            qdrantClient.deleteAsync(qdrantProperties.getCollection(), pointIds, null).get();
            log.info("Batch deleted {} points from Qdrant", ids.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant batch delete interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant batch delete failed", e.getCause());
        }
    }

    public void updateVisibility(Long id, Visibility visibility) {
        Map<String, JsonWithInt.Value> payloadUpdate = Map.of(
            QdrantPayloadFields.VISIBILITY,
            ValueFactory.value(visibility.getValue())
        );

        try {
            qdrantClient.setPayloadAsync(
                qdrantProperties.getCollection(),
                payloadUpdate,
                PointIdFactory.id(id),
                null, null, null
            ).get();
            log.debug("Updated visibility for question: id={} to {}", id, visibility);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant setPayload interrupted: " + id, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant setPayload failed: " + id, e.getCause());
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
            if (isCollectionNotFound(e)) {
                log.debug("Collection not found, skipping Qdrant delete for summary: {}", summaryId);
                return;
            }
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
                    String id = sp.getId().hasNum()
                        ? String.valueOf(sp.getId().getNum())
                        : sp.getId().getUuid().replace("-", "");
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

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /**
     * 检查是否为 Qdrant collection 不存在的错误。
     * Qdrant 报错格式为 "Collection <name> doesn't exist!"。
     * 必须同时匹配 "collection" 和 "doesn't exist" 避免误判其他错误。
     */
    private static boolean isCollectionNotFound(ExecutionException e) {
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            String msg = e.getCause().getMessage().toLowerCase();
            return msg.contains("collection") && msg.contains("doesn't exist");
        }
        return false;
    }
}
