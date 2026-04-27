package com.zju.offercatcher.infrastructure.tools;

/**
 * 用户工具上下文
 *
 * 注入到 ToolExecutionContext 中，使所有 Tool 能获取当前 userId。
 * 对应 Python: app/application/agents/chat/runtime.py UserContext
 */
public record UserToolContext(String userId) {

    public static final String KEY = "userContext";
}
