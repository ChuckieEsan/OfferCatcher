package com.zju.offercatcher.domain.question.repositories;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.valueobjects.QuestionWithScore;
import com.zju.offercatcher.domain.shared.enums.Visibility;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 题目仓储接口
 *
 * 设计原则：
 * - 定义在 Domain 层，实现由 Infrastructure 层提供
 * - 包含用户隔离查询方法
 * - 所有修改操作需验证用户权限
 */
public interface QuestionRepository {

    // ==================== 用户隔离查询方法 ====================

    /**
     * 搜索用户可见的题目（公共 + 用户私有）
     * @param userId 请求用户 ID
     * @param queryVector 查询向量
     * @param limit 返回数量
     * @return 按相似度排序的题目列表
     */
    List<QuestionWithScore> searchUserVisible(String userId, float[] queryVector, int limit);

    /**
     * 搜索用户可见的题目（带过滤条件）
     * @param userId 请求用户 ID
     * @param queryVector 查询向量
     * @param filters 过滤条件（company, position, type 等）
     * @param limit 返回数量
     * @return 按相似度排序的题目列表
     */
    List<QuestionWithScore> searchUserVisible(String userId, float[] queryVector,
                                               Map<String, Object> filters, int limit);

    /**
     * 仅搜索公共题目
     * @param queryVector 查询向量
     * @param limit 返回数量
     * @return 按相似度排序的公共题目列表
     */
    List<QuestionWithScore> searchPublicOnly(float[] queryVector, int limit);

    /**
     * 仅搜索用户私有题目
     * @param userId 用户 ID
     * @param queryVector 查询向量
     * @param limit 返回数量
     * @return 按相似度排序的用户私有题目列表
     */
    List<QuestionWithScore> searchPrivateOnly(String userId, float[] queryVector, int limit);

    /**
     * 获取用户指定可见性的题目数量
     * @param userId 用户 ID
     * @param visibility 可见性类型
     * @return 题目数量
     */
    long countByUserIdAndVisibility(String userId, Visibility visibility);

    // ==================== 基本 CRUD ====================

    /**
     * 根据ID查找题目
     * @param questionId 题目 ID
     * @return 题目实体（Optional）
     */
    Optional<Question> findById(String questionId);

    /**
     * 保存题目
     * @param question 题目实体
     */
    void save(Question question);

    /**
     * 删除题目（需验证所有权）
     * @param questionId 题目 ID
     * @param userId 请求用户 ID（验证所有权）
     * @throws UnauthorizedOperationException 如果用户不是所有者
     */
    void deleteById(String questionId, String userId);

    /**
     * 发布题目到公共题库
     * @param questionId 题目 ID
     * @param userId 所有者 ID
     * @throws UnauthorizedOperationException 如果用户不是所有者
     */
    void publishToPublic(String questionId, String userId);

    // ==================== 批量操作 ====================

    /**
     * 批量保存题目
     * @param questions 题目列表
     */
    void saveAll(List<Question> questions);

    /**
     * 按用户查询题目列表（分页）
     * @param userId 用户 ID
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     * @return 用户的所有题目（包括私有和已发布的公共）
     */
    List<Question> findByUserId(String userId, int page, int size);

    /**
     * 查询公共题目列表（分页）
     * @param page 页码（从 0 开始）
     * @param size 每页数量
     * @return 公共题目列表
     */
    List<Question> findPublicQuestions(int page, int size);

    /**
     * 按公司和用户查询题目
     * @param userId 用户 ID
     * @param company 公司名称
     * @param page 页码
     * @param size 每页数量
     * @return 用户可见的该公司题目
     */
    List<Question> findByCompanyForUser(String userId, String company, int page, int size);
}