package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.service.StatsApplicationService;
import com.zju.offercatcher.interfaces.dto.StatsDto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    StatsApplicationService statsService;

    @Nested
    @DisplayName("GET /api/v1/stats/overview")
    class GetOverview {

        @Test
        @DisplayName("返回总览统计")
        void overview() throws Exception {
            when(statsService.getOverview()).thenReturn(new OverviewStats(
                    100, 10, 5, Map.of("knowledge", 80, "coding", 20),
                    Map.of(0, 30, 1, 40, 2, 30), 60, 40));

            mvc.perform(get("/api/v1/stats/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalQuestions").value(100))
                    .andExpect(jsonPath("$.totalCompanies").value(10))
                    .andExpect(jsonPath("$.byType.knowledge").value(80));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/stats/companies")
    class GetCompanies {

        @Test
        @DisplayName("返回公司统计列表")
        void companies() throws Exception {
            when(statsService.getCompanyStats()).thenReturn(List.of(
                    new CompanyStats("阿里巴巴", 50, 20, 35),
                    new CompanyStats("字节跳动", 30, 15, 20)));

            mvc.perform(get("/api/v1/stats/companies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].company").value("阿里巴巴"))
                    .andExpect(jsonPath("$[0].count").value(50));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/stats/positions")
    class GetPositions {

        @Test
        @DisplayName("返回岗位统计列表")
        void positions() throws Exception {
            when(statsService.getPositionStats()).thenReturn(List.of(
                    new PositionStats("Java 后端", 40),
                    new PositionStats("前端", 25)));

            mvc.perform(get("/api/v1/stats/positions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].position").value("Java 后端"))
                    .andExpect(jsonPath("$[0].count").value(40));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/stats/entities")
    class GetEntities {

        @Test
        @DisplayName("返回考点统计列表")
        void entities() throws Exception {
            when(statsService.getEntityStats(null, 20)).thenReturn(List.of(
                    new EntityStats("HashMap", 15),
                    new EntityStats("JVM", 10)));

            mvc.perform(get("/api/v1/stats/entities"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].entity").value("HashMap"))
                    .andExpect(jsonPath("$[0].count").value(15));
        }

        @Test
        @DisplayName("支持按公司过滤")
        void entitiesByCompany() throws Exception {
            when(statsService.getEntityStats("阿里巴巴", 10)).thenReturn(List.of(
                    new EntityStats("HashMap", 8)));

            mvc.perform(get("/api/v1/stats/entities?company=阿里巴巴&limit=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].entity").value("HashMap"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/stats/clusters")
    class GetClusters {

        @Test
        @DisplayName("返回聚类统计列表")
        void clusters() throws Exception {
            when(statsService.getClusterStats()).thenReturn(List.of(
                    new ClusterStats("cluster-1", 20),
                    new ClusterStats("cluster-2", 15)));

            mvc.perform(get("/api/v1/stats/clusters"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].clusterId").value("cluster-1"))
                    .andExpect(jsonPath("$[0].count").value(20));
        }
    }
}
