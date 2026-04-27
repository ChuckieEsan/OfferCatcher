package com.zju.offercatcher.integration;

import com.zju.offercatcher.interfaces.dto.QuestionDto;
import com.zju.offercatcher.interfaces.dto.ExtractDto.*;
import com.zju.offercatcher.interfaces.dto.FavoriteDto;
import com.zju.offercatcher.interfaces.dto.StatsDto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfferCatcherEndToEndTest {

    @LocalServerPort int port;

    @Autowired TestRestTemplate rest;

    private static final String USER = "e2e-test-user";
    private String baseUrl;
    private HttpHeaders headers;

    private String createdQuestionId;
    private Long createdTaskId;
    private Long createdFavoriteId;

    @BeforeAll
    void setUp() {
        baseUrl = "http://localhost:" + port;
        headers = new HttpHeaders();
        headers.set("X-User-Id", USER);
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    // ==================== Question E2E ====================

    @Test
    @Order(1)
    @DisplayName("E2E: Question CRUD — create, get, list, update, delete")
    void questionCrudFlow() {
        // CREATE
        Map<String, Object> body = Map.of(
            "questionText", "ConcurrentHashMap JDK7 vs JDK8 区别？",
            "company", "阿里巴巴",
            "position", "Java 后端",
            "questionType", "knowledge",
            "coreEntities", List.of("ConcurrentHashMap", "JUC")
        );
        ResponseEntity<QuestionDto.Response> resp = rest.exchange(
            baseUrl + "/api/v1/questions",
            HttpMethod.POST, new HttpEntity<>(body, headers), QuestionDto.Response.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().company()).isEqualTo("阿里巴巴");
        createdQuestionId = resp.getBody().questionId();
        assertThat(createdQuestionId).isNotBlank();

        // GET by ID
        ResponseEntity<QuestionDto.Response> getResp = rest.exchange(
            baseUrl + "/api/v1/questions/" + createdQuestionId,
            HttpMethod.GET, new HttpEntity<>(headers), QuestionDto.Response.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().questionText()).contains("ConcurrentHashMap");

        // LIST — user isolation
        HttpHeaders otherH = new HttpHeaders();
        otherH.set("X-User-Id", "other-user");
        otherH.setContentType(MediaType.APPLICATION_JSON);
        rest.postForEntity(baseUrl + "/api/v1/questions",
            new HttpEntity<>(Map.of(
                "questionText", "Other user question",
                "company", "字节", "position", "Go",
                "questionType", "coding", "coreEntities", List.of("goroutine")),
                otherH), QuestionDto.Response.class);

        ResponseEntity<QuestionDto.ListResponse> listResp = rest.exchange(
            baseUrl + "/api/v1/questions?page=1&pageSize=10",
            HttpMethod.GET, new HttpEntity<>(headers), QuestionDto.ListResponse.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<QuestionDto.Response> questions = listResp.getBody().questions();
        assertThat(questions.stream()
            .noneMatch(q -> "Other user question".equals(q.questionText()))).isTrue();

        // UPDATE
        ResponseEntity<QuestionDto.Response> updateResp = rest.exchange(
            baseUrl + "/api/v1/questions/" + createdQuestionId,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("answer", "JDK7分段锁，JDK8 CAS+synchronized"),
                headers), QuestionDto.Response.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody().questionAnswer()).contains("分段锁");

        // DELETE
        ResponseEntity<Void> deleteResp = rest.exchange(
            baseUrl + "/api/v1/questions/" + createdQuestionId,
            HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify 404
        assertThat(rest.exchange(
            baseUrl + "/api/v1/questions/" + createdQuestionId,
            HttpMethod.GET, new HttpEntity<>(headers), QuestionDto.Response.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Re-create for downstream tests
        ResponseEntity<QuestionDto.Response> recreate = rest.exchange(
            baseUrl + "/api/v1/questions",
            HttpMethod.POST, new HttpEntity<>(body, headers), QuestionDto.Response.class);
        createdQuestionId = recreate.getBody().questionId();
    }

    // ==================== ExtractTask E2E ====================

    @Test
    @Order(2)
    @DisplayName("E2E: ExtractTask — submit, list, get, cancel, delete")
    void extractTaskLifecycle() {
        // SUBMIT
        ResponseEntity<SubmitResponse> submitResp = rest.exchange(
            baseUrl + "/api/v1/extract/submit",
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceType", "text",
                "sourceContent", "阿里巴巴 Java 面试：HashMap原理、JVM调优",
                "sourceImages", List.of()), headers),
            SubmitResponse.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitResp.getBody().message()).contains("已提交");
        createdTaskId = submitResp.getBody().taskId();
        assertThat(createdTaskId).isPositive();

        // LIST
        ResponseEntity<TaskListResponse> listResp = rest.exchange(
            baseUrl + "/api/v1/extract/tasks?status=pending",
            HttpMethod.GET, new HttpEntity<>(headers), TaskListResponse.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().items().stream()
            .anyMatch(t -> t.taskId().equals(createdTaskId))).isTrue();

        // GET detail
        ResponseEntity<TaskResponse> getResp = rest.exchange(
            baseUrl + "/api/v1/extract/tasks/" + createdTaskId,
            HttpMethod.GET, new HttpEntity<>(headers), TaskResponse.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().status()).isEqualTo("pending");

        // CANCEL
        ResponseEntity<Void> cancelResp = rest.exchange(
            baseUrl + "/api/v1/extract/tasks/" + createdTaskId + "/cancel",
            HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE
        ResponseEntity<Void> deleteResp = rest.exchange(
            baseUrl + "/api/v1/extract/tasks/" + createdTaskId,
            HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ==================== Favorite E2E ====================

    @Test
    @Order(3)
    @DisplayName("E2E: Favorite — add, list, check, delete")
    void favoriteCrudFlow() {
        // ADD
        ResponseEntity<FavoriteDto.Response> resp = rest.exchange(
            baseUrl + "/api/v1/favorites",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("questionId", createdQuestionId), headers),
            FavoriteDto.Response.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().questionId()).isEqualTo(createdQuestionId);
        createdFavoriteId = resp.getBody().favoriteId();

        // LIST
        ResponseEntity<FavoriteDto.ListResponse> listResp = rest.exchange(
            baseUrl + "/api/v1/favorites?page=0&pageSize=50",
            HttpMethod.GET, new HttpEntity<>(headers), FavoriteDto.ListResponse.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().favorites().stream()
            .anyMatch(f -> f.favoriteId().equals(createdFavoriteId))).isTrue();

        // CHECK
        ResponseEntity<FavoriteDto.CheckResponse> checkResp = rest.exchange(
            baseUrl + "/api/v1/favorites/check",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("questionIds",
                List.of(createdQuestionId, "no-such-id")), headers),
            FavoriteDto.CheckResponse.class);
        assertThat(checkResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkResp.getBody().favorited().get(createdQuestionId)).isTrue();
        assertThat(checkResp.getBody().favorited().get("no-such-id")).isFalse();

        // DELETE
        ResponseEntity<Void> deleteResp = rest.exchange(
            baseUrl + "/api/v1/favorites/" + createdFavoriteId,
            HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ==================== Stats E2E ====================

    @Test
    @Order(4)
    @DisplayName("E2E: Stats dashboard — all 5 endpoints return data")
    void statsEndpoints() {
        ResponseEntity<OverviewStats> overview = rest.exchange(
            baseUrl + "/api/v1/stats/overview",
            HttpMethod.GET, new HttpEntity<>(headers), OverviewStats.class);
        assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overview.getBody().totalQuestions()).isGreaterThanOrEqualTo(1);

        ResponseEntity<CompanyStats[]> companies = rest.exchange(
            baseUrl + "/api/v1/stats/companies",
            HttpMethod.GET, new HttpEntity<>(headers), CompanyStats[].class);
        assertThat(companies.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(companies.getBody().length).isGreaterThanOrEqualTo(1);

        ResponseEntity<PositionStats[]> positions = rest.exchange(
            baseUrl + "/api/v1/stats/positions",
            HttpMethod.GET, new HttpEntity<>(headers), PositionStats[].class);
        assertThat(positions.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<EntityStats[]> entities = rest.exchange(
            baseUrl + "/api/v1/stats/entities?company=阿里巴巴&limit=10",
            HttpMethod.GET, new HttpEntity<>(headers), EntityStats[].class);
        assertThat(entities.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ClusterStats[]> clusters = rest.exchange(
            baseUrl + "/api/v1/stats/clusters",
            HttpMethod.GET, new HttpEntity<>(headers), ClusterStats[].class);
        assertThat(clusters.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==================== Cleanup ====================

    @Test
    @Order(99)
    @DisplayName("E2E: Cleanup")
    void cleanup() {
        rest.exchange(baseUrl + "/api/v1/questions/" + createdQuestionId,
            HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
