package com.zju.offercatcher.infrastructure.adapters.websearch;

/**
 * Web 搜索结果值对象
 */
public record WebSearchResult(String title, String url, String snippet, String content) {
}
