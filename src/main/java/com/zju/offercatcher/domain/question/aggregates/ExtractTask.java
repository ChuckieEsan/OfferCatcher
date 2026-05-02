package com.zju.offercatcher.domain.question.aggregates;

import com.zju.offercatcher.domain.question.valueobjects.ExtractedQuestionItem;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面经提取任务聚合根。
 *
 * 管理面经提取的完整生命周期：提交 -> 处理 -> 完成 -> 确认入库。
 * 用户确认后才触发 Question 入库。
 */
@Getter
public class ExtractTask {

    private final Long taskId;
    private final String userId;
    private final String sourceType;
    private String sourceContent;
    private List<String> sourceImages;
    private ExtractTaskStatus status;
    private ExtractedQuestionItem extractedInterview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ExtractTask(Long taskId, String userId, String sourceType, String sourceContent,
                        List<String> sourceImages, ExtractTaskStatus status,
                        ExtractedQuestionItem extractedInterview,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.taskId = taskId;
        this.userId = userId;
        this.sourceType = sourceType;
        this.sourceContent = sourceContent;
        this.sourceImages = sourceImages;
        this.status = status;
        this.extractedInterview = extractedInterview;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ExtractTask create(String userId, String sourceType,
                                     String sourceContent, List<String> sourceImages) {
        LocalDateTime now = LocalDateTime.now();
        return new ExtractTask(null, userId, sourceType,
            sourceContent != null ? sourceContent : "",
            sourceImages, ExtractTaskStatus.PENDING, null, now, now);
    }

    public static ExtractTask rebuild(Long taskId, String userId, String sourceType,
                                      String sourceContent, List<String> sourceImages,
                                      ExtractTaskStatus status,
                                      ExtractedQuestionItem extractedInterview,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ExtractTask(taskId, userId, sourceType,
            sourceContent != null ? sourceContent : "",
            sourceImages, status, extractedInterview, createdAt, updatedAt);
    }

    public void startProcessing() {
        if (status != ExtractTaskStatus.PENDING) {
            throw new InvalidStateException("Cannot start from status: " + status.name());
        }
        status = ExtractTaskStatus.PROCESSING;
        updatedAt = LocalDateTime.now();
    }

    public void complete(ExtractedQuestionItem result) {
        if (status != ExtractTaskStatus.PROCESSING) {
            throw new InvalidStateException("Cannot complete from status: " + status.name());
        }
        status = ExtractTaskStatus.COMPLETED;
        this.extractedInterview = result;
        updatedAt = LocalDateTime.now();
    }

    public void confirm() {
        if (status != ExtractTaskStatus.COMPLETED) {
            throw new InvalidStateException("Cannot confirm from status: " + status.name());
        }
        status = ExtractTaskStatus.CONFIRMED;
        updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status != ExtractTaskStatus.PENDING && status != ExtractTaskStatus.PROCESSING) {
            throw new InvalidStateException("Cannot cancel from status: " + status.name());
        }
        status = ExtractTaskStatus.CANCELLED;
        updatedAt = LocalDateTime.now();
    }

    public void editResult(String company, String position,
                           List<ExtractedQuestionItem.QuestionItem> questions) {
        this.extractedInterview = new ExtractedQuestionItem(company, position, questions);
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }

    public String getCompanyFromResult() {
        if (extractedInterview == null) return "";
        return extractedInterview.company() != null ? extractedInterview.company() : "";
    }

    public String getPositionFromResult() {
        if (extractedInterview == null) return "";
        return extractedInterview.position() != null ? extractedInterview.position() : "";
    }

    public int getQuestionCount() {
        if (extractedInterview == null) return 0;
        return extractedInterview.questions().size();
    }
}
