package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.persistence.neo4j.Neo4jClient;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聚类应用服务
 *
 * 使用 KMeans 进行题目聚类，支持自动选择最优簇数（轮廓系数）。
 * 对应 Python: app/application/services/clustering_service.py
 */
@Service
public class ClusteringApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ClusteringApplicationService.class);

    private final QuestionJpaRepository questionJpaRepo;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final Neo4jClient neo4jClient;

    public ClusteringApplicationService(QuestionJpaRepository questionJpaRepo,
                                         OnnxEmbeddingAdapter embeddingAdapter,
                                         Neo4jClient neo4jClient) {
        this.questionJpaRepo = questionJpaRepo;
        this.embeddingAdapter = embeddingAdapter;
        this.neo4jClient = neo4jClient;
    }

    public ClusteringResult runClustering(Integer k) {
        if (!embeddingAdapter.isInitialized()) {
            log.warn("Embedding adapter not initialized, skipping clustering");
            return new ClusteringResult(0, 0, 0, 0.0);
        }

        List<QuestionJpaEntity> entities = questionJpaRepo.findAll();
        if (entities.isEmpty()) {
            log.warn("No questions found for clustering");
            return new ClusteringResult(0, 0, 0, 0.0);
        }

        log.info("Starting clustering on {} questions", entities.size());

        // 1. Build context text for each question and generate embeddings
        List<String> texts = new ArrayList<>();
        for (QuestionJpaEntity q : entities) {
            texts.add(q.toDomain().toContext());
        }

        log.info("Generating embeddings for {} questions...", texts.size());
        List<float[]> vectors = new ArrayList<>();
        for (String text : texts) {
            vectors.add(embeddingAdapter.embed(text));
        }

        // 2. Normalize vectors
        for (float[] vec : vectors) {
            l2Normalize(vec);
        }

        // 3. Determine K
        int nSamples = vectors.size();
        int nClusters;
        if (k != null) {
            nClusters = k;
        } else {
            int maxK = Math.min(30, nSamples / 5);
            int minK = Math.max(2, maxK / 4);
            nClusters = selectOptimalK(vectors, minK, maxK);
        }

        log.info("Running KMeans with K={}", nClusters);

        // 4. KMeans
        int[] labels = kmeans(vectors, nClusters);

        // 5. Silhouette score
        double silScore = silhouetteScore(vectors, labels, nClusters, Math.min(1000, nSamples));

        // 6. Build cluster → question indices
        Map<Integer, List<Integer>> clusterToIndices = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            clusterToIndices.computeIfAbsent(labels[i], x -> new ArrayList<>()).add(i);
        }

        // 7. Generate cluster IDs and knowledge points
        Map<Integer, String> clusterIdMap = new HashMap<>();
        for (var entry : clusterToIndices.entrySet()) {
            int label = entry.getKey();
            List<Integer> indices = entry.getValue();

            List<String> knowledgePoints = extractTopEntities(indices, entities);
            String clusterId = generateClusterId(knowledgePoints);
            clusterIdMap.put(label, clusterId);

            String clusterName = knowledgePoints.isEmpty() ? "未命名" : knowledgePoints.getFirst();
            String summary = "包含 " + indices.size() + " 道题目";

            neo4jClient.createClusterNode(clusterId, clusterName, summary);
            for (String kp : knowledgePoints) {
                neo4jClient.createRelatedToRelationship(clusterId, kp);
            }

            log.info("Cluster {}: {} questions, knowledge_points: {}", label, indices.size(), knowledgePoints);
        }

        // 8. Update question cluster_ids and sync to Neo4j
        int clusteredCount = 0;
        for (int i = 0; i < labels.length; i++) {
            QuestionJpaEntity entity = entities.get(i);
            String clusterId = clusterIdMap.get(labels[i]);

            List<String> currentClusters = entity.getClusterIds();
            if (currentClusters == null) {
                currentClusters = new ArrayList<>();
            }
            if (!currentClusters.contains(clusterId)) {
                currentClusters.add(clusterId);
                entity.setClusterIds(currentClusters);
            }

            neo4jClient.createBelongsToRelationship(entity.getQuestionHash(), clusterId);
            clusteredCount++;
        }

        questionJpaRepo.saveAll(entities);

        log.info("Clustering complete: {} clusters, {} questions updated, silhouette={:.4f}",
            nClusters, clusteredCount, silScore);

        return new ClusteringResult(entities.size(), clusteredCount, nClusters, silScore);
    }

    // ==================== KMeans ====================

    private int[] kmeans(List<float[]> vectors, int k) {
        int n = vectors.size();
        int dim = vectors.getFirst().length;
        int[] labels = new int[n];

        // Initialize centroids randomly from data points
        Random rng = new Random(42);
        float[][] centroids = new float[k][dim];
        Set<Integer> chosen = new HashSet<>();
        for (int c = 0; c < k; c++) {
            int idx;
            do { idx = rng.nextInt(n); } while (chosen.contains(idx));
            chosen.add(idx);
            System.arraycopy(vectors.get(idx), 0, centroids[c], 0, dim);
        }

        boolean changed = true;
        int maxIters = 100;
        int iter = 0;

        while (changed && iter < maxIters) {
            changed = false;
            // Assign step
            for (int i = 0; i < n; i++) {
                float[] vec = vectors.get(i);
                int bestCluster = 0;
                double bestDist = euclideanSq(vec, centroids[0]);
                for (int c = 1; c < k; c++) {
                    double dist = euclideanSq(vec, centroids[c]);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestCluster = c;
                    }
                }
                if (labels[i] != bestCluster) {
                    labels[i] = bestCluster;
                    changed = true;
                }
            }

            // Update step
            for (int c = 0; c < k; c++) {
                Arrays.fill(centroids[c], 0.0f);
                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (labels[i] == c) {
                        float[] vec = vectors.get(i);
                        for (int d = 0; d < dim; d++) {
                            centroids[c][d] += vec[d];
                        }
                        count++;
                    }
                }
                if (count > 0) {
                    for (int d = 0; d < dim; d++) {
                        centroids[c][d] /= count;
                    }
                }
            }
            iter++;
        }

        return labels;
    }

    // ==================== Silhouette Score ====================

    private double silhouetteScore(List<float[]> vectors, int[] labels, int k, int sampleSize) {
        int n = vectors.size();
        int useN = Math.min(n, sampleSize);
        Random rng = new Random(42);

        // Pre-build cluster membership
        Map<Integer, List<Integer>> clusters = new HashMap<>();
        for (int i = 0; i < n; i++) {
            clusters.computeIfAbsent(labels[i], x -> new ArrayList<>()).add(i);
        }

        double totalScore = 0.0;
        for (int s = 0; s < useN; s++) {
            int i = rng.nextInt(n);
            int myCluster = labels[i];
            List<Integer> sameCluster = clusters.get(myCluster);

            // a(i): mean distance to points in same cluster
            double a = 0;
            if (sameCluster.size() > 1) {
                for (int j : sameCluster) {
                    if (j != i) a += Math.sqrt(euclideanSq(vectors.get(i), vectors.get(j)));
                }
                a /= (sameCluster.size() - 1);
            }

            // b(i): min mean distance to points in another cluster
            double b = Double.MAX_VALUE;
            for (var entry : clusters.entrySet()) {
                if (entry.getKey() == myCluster) continue;
                double sum = 0;
                for (int j : entry.getValue()) {
                    sum += Math.sqrt(euclideanSq(vectors.get(i), vectors.get(j)));
                }
                double avg = sum / entry.getValue().size();
                if (avg < b) b = avg;
            }
            if (b == Double.MAX_VALUE) b = 0;

            totalScore += (b - a) / Math.max(a, b);
        }

        return totalScore / useN;
    }

    private int selectOptimalK(List<float[]> vectors, int minK, int maxK) {
        double bestScore = -1;
        int bestK = minK;

        for (int k = minK; k <= maxK; k++) {
            int[] labels = kmeans(vectors, k);
            double score = silhouetteScore(vectors, labels, k, Math.min(1000, vectors.size()));
            log.debug("K={}, silhouette_score={:.4f}", k, score);
            if (score > bestScore) {
                bestScore = score;
                bestK = k;
            }
        }

        log.info("Selected optimal K={} with silhouette_score={:.4f}", bestK, bestScore);
        return bestK;
    }

    // ==================== Helpers ====================

    private static double euclideanSq(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = (double) a[i] - (double) b[i];
            sum += diff * diff;
        }
        return sum;
    }

    private static void l2Normalize(float[] vec) {
        double norm = 0;
        for (float v : vec) norm += (double) v * (double) v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) vec[i] = (float) (vec[i] / norm);
        }
    }

    private List<String> extractTopEntities(List<Integer> indices, List<QuestionJpaEntity> entities) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (int idx : indices) {
            List<String> entityList = entities.get(idx).getCoreEntities();
            if (entityList != null) {
                for (String e : entityList) {
                    counter.merge(e, 1, Integer::sum);
                }
            }
        }
        return counter.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .toList();
    }

    private String generateClusterId(List<String> knowledgePoints) {
        if (knowledgePoints.isEmpty()) {
            return "cluster_unknown_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
        return "cluster_" + String.join("_", knowledgePoints.stream().limit(3).toList());
    }

    // ==================== Result DTO ====================

    public record ClusteringResult(int totalQuestions, int clusteredCount, int clusterCount, double silhouetteScore) {}
}
