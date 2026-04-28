package com.zju.offercatcher.domain.question.aggregates;

import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.domain.shared.enums.SourceType;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.question.services.QuestionIdGenerator;
import com.zju.offercatcher.infrastructure.common.SnowflakeIdGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目聚合根
 *
 * 设计原则：
 * - 包含用户隔离字段（userId, visibility, sourceType）
 * - 通过工厂方法创建，确保 ID 生成一致性
 * - 业务逻辑封装在聚合内部，不暴露 setter
 */
public class Question {

    // ==================== 标识字段 ====================
    private final Long id;                // Snowflake 主键 (数据库 PK)
    private final String questionHash;    // MD5(userId|company|questionText) 业务去重键
    private final String userId;          // 题目所有者（用户隔离）

    // ==================== 内容字段 ====================
    private String questionText;
    private final QuestionType questionType;
    private MasteryLevel masteryLevel;
    private final String company;
    private final String position;
    private List<String> coreEntities;
    private String answer;
    private List<String> clusterIds;
    private Map<String, Object> metadata;

    // ==================== 用户隔离字段 ====================
    private Visibility visibility;  // PUBLIC / PRIVATE（可发布到公共题库）
    private final SourceType sourceType;  // USER_UPLOAD / SYSTEM_IMPORT

    // ==================== 时间字段 ====================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== 工厂方法 ====================

    /**
     * 创建用户私有题目
     * @param userId 用户 ID
     * @param questionText 题目文本
     * @param company 公司名称
     * @param position 岗位名称
     * @param questionType 题目类型
     * @param coreEntities 核心考点列表
     * @return 新创建的私有题目
     */
    public static Question createPrivate(String userId, String questionText,
                                          String company, String position,
                                          QuestionType questionType, List<String> coreEntities) {
        Long id = SnowflakeIdGenerator.generate();
        String hash = QuestionIdGenerator.generate(userId, company, questionText);
        return new Question(id, hash, userId, questionText, questionType, company, position,
                            coreEntities, Visibility.PRIVATE, SourceType.USER_UPLOAD);
    }

    /**
     * 创建用户公共题目
     * @param userId 用户 ID
     * @param questionText 题目文本
     * @param company 公司名称
     * @param position 岗位名称
     * @param questionType 题目类型
     * @param coreEntities 核心考点列表
     * @return 新创建的公共题目
     */
    public static Question createPublic(String userId, String questionText,
                                          String company, String position,
                                          QuestionType questionType, List<String> coreEntities) {
        Long id = SnowflakeIdGenerator.generate();
        String hash = QuestionIdGenerator.generate(userId, company, questionText);
        return new Question(id, hash, userId, questionText, questionType, company, position,
                            coreEntities, Visibility.PUBLIC, SourceType.USER_UPLOAD);
    }

    /**
     * 创建系统导入的公共题目（初始题库）
     * @param questionText 题目文本
     * @param company 公司名称
     * @param position 岗位名称
     * @param questionType 题目类型
     * @param coreEntities 核心考点列表
     * @return 新创建的系统题目
     */
    public static Question createSystemImport(String questionText,
                                               String company, String position,
                                               QuestionType questionType, List<String> coreEntities) {
        Long id = SnowflakeIdGenerator.generate();
        String hash = QuestionIdGenerator.generateSystemId(company, questionText);
        return new Question(id, hash, "system", questionText, questionType, company, position,
                            coreEntities, Visibility.PUBLIC, SourceType.SYSTEM_IMPORT);
    }

    /**
     * 从持久化存储重建题目（用于 Repository 实现）
     */
    public static Question rebuild(Long id, String questionHash, String userId, String questionText,
                                    QuestionType questionType, String company, String position,
                                    List<String> coreEntities, String answer, MasteryLevel masteryLevel,
                                    List<String> clusterIds, Map<String, Object> metadata,
                                    Visibility visibility, SourceType sourceType,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Question(id, questionHash, userId, questionText, questionType, company, position,
                            coreEntities, answer, masteryLevel, clusterIds, metadata,
                            visibility, sourceType, createdAt, updatedAt);
    }

    // ==================== 业务方法 ====================

    /**
     * 更新答案
     * @param newAnswer 新答案内容
     */
    public void updateAnswer(String newAnswer) {
        this.answer = newAnswer;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新题目内容和知识点
     * @param newQuestionText 新题目文本
     * @param newCoreEntities 新知识点列表
     */
    public void updateContent(String newQuestionText, List<String> newCoreEntities) {
        if (newQuestionText != null && !newQuestionText.isBlank()) {
            this.questionText = newQuestionText;
        }
        if (newCoreEntities != null) {
            this.coreEntities = new ArrayList<>(newCoreEntities);
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加考点簇
     * @param clusterId 考点簇 ID
     */
    public void addCluster(String clusterId) {
        if (clusterId != null && !clusterId.isBlank() && !this.clusterIds.contains(clusterId)) {
            this.clusterIds.add(clusterId);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 更新熟练度
     * @param level 新熟练度等级
     */
    public void updateMastery(MasteryLevel level) {
        this.masteryLevel = level;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 发布到公共题库
     * 只有私有题目才能发布
     */
    public void publishToPublic() {
        if (this.visibility != Visibility.PRIVATE) {
            throw new DomainException("只有私有题目才能发布到公共题库", "INVALID_VISIBILITY_TRANSITION");
        }
        this.visibility = Visibility.PUBLIC;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断是否对用户可见
     * @param requestingUserId 请求用户 ID
     * @return true 如果用户可见
     */
    public boolean isVisibleTo(String requestingUserId) {
        return this.visibility == Visibility.PUBLIC
            || this.userId.equals(requestingUserId);
    }

    /**
     * 判断是否为用户所有
     * @param requestingUserId 请求用户 ID
     * @return true 如果是所有者
     */
    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    /**
     * 判断是否需要异步生成详细答案
     */
    public boolean requiresAsyncAnswer() {
        return questionType.requiresAsyncAnswer();
    }

    /**
     * 生成 Embedding 上下文文本
     * 用于向量化和检索
     */
    public String toContext() {
        String entities = coreEntities.isEmpty() ? "综合"
            : String.join(",", coreEntities);
        return String.format("公司：%s | 岗位：%s | 类型：%s | 考点：%s | 题目：%s",
            company, position, questionType.getValue(), entities, questionText);
    }

    // ==================== Getter 方法 ====================

    public Long getId() {
        return id;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public MasteryLevel getMasteryLevel() {
        return masteryLevel;
    }

    public String getCompany() {
        return company;
    }

    public String getPosition() {
        return position;
    }

    public List<String> getCoreEntities() {
        return Collections.unmodifiableList(coreEntities);
    }

    public String getAnswer() {
        return answer;
    }

    public List<String> getClusterIds() {
        return Collections.unmodifiableList(clusterIds);
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // ==================== 构造函数 ====================

    private Question(Long id, String questionHash, String userId, String questionText,
                      QuestionType questionType, String company, String position,
                      List<String> coreEntities, Visibility visibility, SourceType sourceType) {
        this.id = id;
        this.questionHash = questionHash;
        this.userId = userId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.company = company;
        this.position = position;
        this.coreEntities = new ArrayList<>(coreEntities != null ? coreEntities : Collections.emptyList());
        this.masteryLevel = MasteryLevel.LEVEL_0;
        this.answer = null;
        this.clusterIds = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.visibility = visibility;
        this.sourceType = sourceType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private Question(Long id, String questionHash, String userId, String questionText,
                      QuestionType questionType, String company, String position,
                      List<String> coreEntities, String answer, MasteryLevel masteryLevel,
                      List<String> clusterIds, Map<String, Object> metadata,
                      Visibility visibility, SourceType sourceType,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.questionHash = questionHash;
        this.userId = userId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.company = company;
        this.position = position;
        this.coreEntities = new ArrayList<>(coreEntities != null ? coreEntities : Collections.emptyList());
        this.answer = answer;
        this.masteryLevel = masteryLevel != null ? masteryLevel : MasteryLevel.LEVEL_0;
        this.clusterIds = new ArrayList<>(clusterIds != null ? clusterIds : Collections.emptyList());
        this.metadata = new HashMap<>(metadata != null ? metadata : Collections.emptyMap());
        this.visibility = visibility;
        this.sourceType = sourceType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}