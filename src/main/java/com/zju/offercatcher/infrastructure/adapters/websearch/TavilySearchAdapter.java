package com.zju.offercatcher.infrastructure.adapters.websearch;

import com.zju.offercatcher.infrastructure.config.WebSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class TavilySearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchAdapter.class);
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final String apiKey;
    private final RestClient restClient;

    public TavilySearchAdapter(WebSearchProperties properties) {
        this.apiKey = properties.getTavilyApiKey();
        this.restClient = RestClient.builder()
            .baseUrl(TAVILY_API_URL)
            .build();
    }

    public List<WebSearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Tavily API key not configured, skipping web search");
            return Collections.emptyList();
        }

        try {
            Map<String, Object> request = Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", maxResults,
                "search_depth", "basic"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

            if (response == null || !response.containsKey("results")) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, String>> results = (List<Map<String, String>>) response.get("results");
            return results.stream()
                .map(r -> new WebSearchResult(
                    r.getOrDefault("title", ""),
                    r.getOrDefault("url", ""),
                    r.getOrDefault("content", ""),
                    r.getOrDefault("content", "")
                ))
                .toList();
        } catch (Exception e) {
            log.error("Tavily search failed for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    public String searchForContext(String question, String company, String position) {
        StringBuilder query = new StringBuilder(company);
        if (position != null && !position.isBlank()) {
            query.append(" ").append(position);
        }
        query.append(" 面试题 ");
        if (question != null && !question.isBlank()) {
            query.append(question);
        }

        List<WebSearchResult> results = search(query.toString(), 5);
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            WebSearchResult r = results.get(i);
            context.append("[").append(i + 1).append("] ").append(r.title()).append("\n");
            context.append(r.snippet()).append("\n\n");
        }
        return context.toString().trim();
    }
}
