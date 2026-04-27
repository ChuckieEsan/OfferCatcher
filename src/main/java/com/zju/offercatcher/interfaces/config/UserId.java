package com.zju.offercatcher.interfaces.config;

import java.lang.annotation.*;

/**
 * 标记从 X-User-Id 请求头提取的用户 ID 参数。
 *
 * <pre>
 * &#064;GetMapping("/conversations")
 * public List&lt;ConversationResponse&gt; list(&#064;UserId String userId) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserId {
}
