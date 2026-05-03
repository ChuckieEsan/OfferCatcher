package com.zju.offercatcher.interfaces.config;

import com.zju.offercatcher.domain.shared.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * 全局异常处理器。
 * <p>
 * 将领域异常映射为 HTTP 4xx/5xx 的 RFC 7807 ProblemDetail 响应。
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingUserIdException.class)
    ProblemDetail handleMissingUserId(MissingUserIdException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Missing Required Header");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/missing-user-id"));
        pd.setProperty("header", ex.getHeaderName());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    ProblemDetail handleUnauthorized(UnauthorizedOperationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Forbidden");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/unauthorized"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("userId", ex.getUserId());
        pd.setProperty("resourceId", ex.getResourceId());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler({
            QuestionNotFoundException.class,
            ConversationNotFoundException.class,
            FavoriteNotFoundException.class,
            InterviewSessionNotFoundException.class,
            MemoryNotFoundException.class
    })
    ProblemDetail handleNotFound(DomainException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/not-found"));
        pd.setProperty("errorCode", ex.getErrorCode());

        Map<Class<?>, String> resourceFields = Map.of(
                QuestionNotFoundException.class, "questionId",
                ConversationNotFoundException.class, "conversationId",
                FavoriteNotFoundException.class, "favoriteId",
                InterviewSessionNotFoundException.class, "sessionId",
                MemoryNotFoundException.class, "userId"
        );
        for (var entry : resourceFields.entrySet()) {
            if (entry.getKey().isInstance(ex)) {
                try {
                    String field = entry.getValue();
                    Object value = ex.getClass().getMethod("get" +
                            field.substring(0, 1).toUpperCase() + field.substring(1)).invoke(ex);
                    pd.setProperty(field, value);
                } catch (Exception ignored) {
                }
                break;
            }
        }
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(InvalidStateException.class)
    ProblemDetail handleInvalidState(InvalidStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Invalid State Transition");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/invalid-state"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        log.warn("Unhandled domain exception: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Domain Error");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/domain-error"));
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/bad-request"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("https://offercatcher.zju.edu/errors/internal-error"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
