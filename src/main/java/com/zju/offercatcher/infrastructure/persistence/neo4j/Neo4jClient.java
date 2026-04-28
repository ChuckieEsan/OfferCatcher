package com.zju.offercatcher.infrastructure.persistence.neo4j;

import com.zju.offercatcher.infrastructure.config.Neo4jProperties;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 图数据库客户端适配器
 *
 * 从 Python app/infrastructure/persistence/neo4j/client.py 移植。
 *
 * 核心功能：
 * - 连接管理（懒加载，首次使用时自动连接）
 * - Company / Entity / Cluster / Question 节点 MERGE
 * - 考频 / RELATED_TO / BELONGS_TO 关系管理
 * - 热门考点、关联知识点、跨公司考点查询
 */
@Service
public class Neo4jClient {

    private static final Logger log = LoggerFactory.getLogger(Neo4jClient.class);

    private final String uri;
    private final String user;
    private final String password;
    private final String database;
    private volatile Driver driver;

    public Neo4jClient(Neo4jProperties properties) {
        this.uri = properties.getUri();
        this.user = properties.getUser();
        this.password = properties.getPassword();
        this.database = properties.getDatabase();
    }

    // ==================== 连接管理 ====================

    public boolean isConnected() {
        return driver != null;
    }

    synchronized boolean ensureConnected() {
        if (driver != null) return true;
        return connect();
    }

    synchronized boolean connect() {
        if (driver != null) return true;
        try {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            driver.verifyConnectivity();
            log.info("Neo4jClient connected: {}", uri);
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to Neo4j: {}", e.getMessage());
            driver = null;
            return false;
        }
    }

    public synchronized void close() {
        if (driver != null) {
            driver.close();
            driver = null;
            log.info("Neo4j connection closed");
        }
    }

    private Session session() {
        if (!ensureConnected() || driver == null) {
            throw new RuntimeException("Neo4j connection not available");
        }
        return driver.session(SessionConfig.forDatabase(database));
    }

    // ==================== 节点操作 ====================

    public boolean createCompanyNode(String company) {
        try (var s = session()) {
            s.run("MERGE (c:Company {name: $company}) RETURN c",
                  Map.of("company", company));
            return true;
        } catch (Exception e) {
            log.error("Failed to create company node: {}", e.getMessage());
            return false;
        }
    }

    public boolean createEntityNode(String entity) {
        try (var s = session()) {
            s.run("MERGE (e:Entity {name: $entity}) RETURN e",
                  Map.of("entity", entity));
            return true;
        } catch (Exception e) {
            log.error("Failed to create entity node: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 考频关系 ====================

    public boolean createExamFrequencyRelationship(String company, String entity, int questionCount) {
        createCompanyNode(company);
        createEntityNode(entity);

        try (var s = session()) {
            s.run("""
                  MATCH (c:Company {name: $company})
                  MATCH (e:Entity {name: $entity})
                  MERGE (c)-[r:考频 {entity: $entity}]->(e)
                  SET r.count = coalesce(r.count, 0) + $count
                  RETURN r
                  """,
                  Map.of("company", company, "entity", entity, "count", questionCount));
            return true;
        } catch (Exception e) {
            log.error("Failed to create 考频 relationship: {}", e.getMessage());
            return false;
        }
    }

    public boolean recordQuestionEntities(String company, List<String> entities) {
        if (entities == null || entities.isEmpty()) return true;
        boolean success = true;
        for (String entity : entities) {
            if (!createExamFrequencyRelationship(company, entity, 1)) {
                success = false;
            }
        }
        return success;
    }

    // ==================== 查询操作 ====================

    /**
     * 获取热门考点，可按公司过滤
     */
    public List<Map<String, Object>> getTopEntities(String company, int limit) {
        try (var s = session()) {
            if (company != null && !company.isEmpty()) {
                var result = s.run("""
                    MATCH (c:Company {name: $company})-[r:考频]->(e:Entity)
                    RETURN e.name as entity, r.count as count
                    ORDER BY r.count DESC
                    LIMIT $limit
                    """, Map.of("company", company, "limit", limit));
                return result.stream()
                    .map(r -> Map.<String, Object>of("entity", r.get("entity"), "count", r.get("count")))
                    .toList();
            } else {
                var result = s.run("""
                    MATCH ()-[r:考频]->(e:Entity)
                    RETURN e.name as entity, sum(r.count) as count
                    ORDER BY count DESC
                    LIMIT $limit
                    """, Map.of("limit", limit));
                return result.stream()
                    .map(r -> Map.<String, Object>of("entity", r.get("entity"), "count", r.get("count")))
                    .toList();
            }
        } catch (Exception e) {
            log.error("Failed to get top entities: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取公司统计信息
     */
    public Map<String, Object> getCompanyStats(String company) {
        try (var s = session()) {
            var result = s.run("""
                MATCH (c:Company {name: $company})
                OPTIONAL MATCH (c)-[r:考频]->(e:Entity)
                RETURN count(DISTINCT e) as entity_count, coalesce(sum(r.count), 0) as total_questions
                """, Map.of("company", company));
            var record = result.single();
            return Map.of(
                "entity_count", record.get("entity_count", 0L),
                "total_questions", record.get("total_questions", 0L)
            );
        } catch (Exception e) {
            log.error("Failed to get company stats: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 获取与指定知识点相关的其他知识点（共现关系）
     */
    public List<Map<String, Object>> getRelatedEntities(String entity, int limit) {
        if (!ensureConnected()) return List.of();

        try (var s = session()) {
            var result = s.run("""
                MATCH (c:Company)-[r1:考频]->(e1:Entity {name: $entity})
                MATCH (c)-[r2:考频]->(e2:Entity)
                WHERE e1 <> e2
                RETURN e2.name as related_entity, sum(r2.count) as co_occurrence_count
                ORDER BY co_occurrence_count DESC
                LIMIT $limit
                """, Map.of("entity", entity, "limit", limit));
            return result.stream()
                .map(r -> Map.<String, Object>of(
                    "related_entity", r.get("related_entity"),
                    "co_occurrence_count", r.get("co_occurrence_count")))
                .toList();
        } catch (Exception e) {
            log.error("Failed to get related entities: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取跨多家公司考察的知识点
     */
    public List<Map<String, Object>> getCrossCompanyEntities(int minCompanies) {
        if (!ensureConnected()) return List.of();

        try (var s = session()) {
            var result = s.run("""
                MATCH (c:Company)-[r:考频]->(e:Entity)
                WITH e.name as entity, collect(c.name) as companies, sum(r.count) as total_count
                WHERE size(companies) >= $minCompanies
                RETURN entity, companies, total_count, size(companies) as company_count
                ORDER BY total_count DESC
                """, Map.of("minCompanies", minCompanies));
            return result.stream()
                .map(r -> Map.<String, Object>of(
                    "entity", r.get("entity"),
                    "companies", r.get("companies"),
                    "total_count", r.get("total_count"),
                    "company_count", r.get("company_count")))
                .toList();
        } catch (Exception e) {
            log.error("Failed to get cross-company entities: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== Cluster 操作 ====================

    public boolean createClusterNode(String clusterId, String clusterName, String summary) {
        if (!ensureConnected()) return false;

        try (var s = session()) {
            s.run("""
                MERGE (c:Cluster {cluster_id: $cluster_id})
                SET c.cluster_name = $cluster_name,
                    c.summary = $summary,
                    c.updated_at = timestamp()
                RETURN c
                """,
                Map.of("cluster_id", clusterId, "cluster_name", clusterName, "summary", summary));
            return true;
        } catch (Exception e) {
            log.error("Failed to create cluster node: {}", e.getMessage());
            return false;
        }
    }

    public boolean createRelatedToRelationship(String clusterId, String knowledgePoint) {
        if (!ensureConnected()) return false;

        createEntityNode(knowledgePoint);

        try (var s = session()) {
            s.run("""
                MATCH (c:Cluster {cluster_id: $cluster_id})
                MATCH (e:Entity {name: $knowledge_point})
                MERGE (c)-[r:RELATED_TO]->(e)
                RETURN r
                """,
                Map.of("cluster_id", clusterId, "knowledge_point", knowledgePoint));
            return true;
        } catch (Exception e) {
            log.error("Failed to create RELATED_TO relationship: {}", e.getMessage());
            return false;
        }
    }

    public boolean createBelongsToRelationship(String questionId, String clusterId) {
        if (!ensureConnected()) return false;

        createQuestionNodeIfNotExists(questionId);

        try (var s = session()) {
            s.run("""
                MATCH (q:Question {question_id: $question_id})
                MATCH (c:Cluster {cluster_id: $cluster_id})
                MERGE (q)-[r:BELONGS_TO]->(c)
                RETURN r
                """,
                Map.of("question_id", questionId, "cluster_id", clusterId));
            return true;
        } catch (Exception e) {
            log.error("Failed to create BELONGS_TO relationship: {}", e.getMessage());
            return false;
        }
    }

    private boolean createQuestionNodeIfNotExists(String questionId) {
        try (var s = session()) {
            s.run("MERGE (q:Question {question_id: $question_id}) RETURN q",
                  Map.of("question_id", questionId));
            return true;
        } catch (Exception e) {
            log.error("Failed to create question node: {}", e.getMessage());
            return false;
        }
    }

    public Optional<Map<String, Object>> getClusterById(String clusterId) {
        if (!ensureConnected()) return Optional.empty();

        try (var s = session()) {
            var result = s.run("""
                MATCH (c:Cluster {cluster_id: $cluster_id})
                OPTIONAL MATCH (q:Question)-[:BELONGS_TO]->(c)
                OPTIONAL MATCH (c)-[:RELATED_TO]->(e:Entity)
                RETURN c.cluster_id as cluster_id,
                       c.cluster_name as cluster_name,
                       c.summary as summary,
                       collect(DISTINCT q.question_id) as question_ids,
                       collect(DISTINCT e.name) as knowledge_points,
                       count(DISTINCT q) as frequency
                """, Map.of("cluster_id", clusterId));
            var record = result.single();
            if (record != null) {
                return Optional.of(Map.of(
                    "cluster_id", record.get("cluster_id"),
                    "cluster_name", record.get("cluster_name"),
                    "summary", record.get("summary", ""),
                    "question_ids", record.get("question_ids", List.of()),
                    "knowledge_points", record.get("knowledge_points", List.of()),
                    "frequency", record.get("frequency", 0L)
                ));
            }
        } catch (Exception e) {
            log.error("Failed to get cluster by id: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public List<Map<String, Object>> getAllClusters(int limit) {
        if (!ensureConnected()) return List.of();

        try (var s = session()) {
            var result = s.run("""
                MATCH (c:Cluster)
                OPTIONAL MATCH (q:Question)-[:BELONGS_TO]->(c)
                RETURN c.cluster_id as cluster_id,
                       c.cluster_name as cluster_name,
                       c.summary as summary,
                       count(DISTINCT q) as frequency
                ORDER BY frequency DESC
                LIMIT $limit
                """, Map.of("limit", limit));
            return result.stream()
                .map(r -> Map.<String, Object>of(
                    "cluster_id", r.get("cluster_id"),
                    "cluster_name", r.get("cluster_name"),
                    "summary", r.get("summary", ""),
                    "frequency", r.get("frequency", 0L)))
                .toList();
        } catch (Exception e) {
            log.error("Failed to get all clusters: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== 删除 ====================

    public boolean deleteCompanies(List<String> companies) {
        if (!ensureConnected()) return false;

        try (var s = session()) {
            for (String company : companies) {
                s.run("MATCH (c:Company {name: $company}) DETACH DELETE c",
                      Map.of("company", company));
            }
            log.debug("Cleaned up companies: {}", companies);
            return true;
        } catch (Exception e) {
            log.error("Failed to cleanup companies: {}", e.getMessage());
            return false;
        }
    }
}
