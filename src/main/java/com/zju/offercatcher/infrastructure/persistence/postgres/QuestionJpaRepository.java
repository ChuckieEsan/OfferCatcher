package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.shared.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Question JPA Repository
 *
 * 用于 PostgreSQL 元数据存储。
 */
@Repository
public interface QuestionJpaRepository extends JpaRepository<QuestionJpaEntity, Long> {

    /**
     * 根据业务键查找
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.questionHash = :questionHash")
    Optional<QuestionJpaEntity> findByQuestionHash(@Param("questionHash") String questionHash);

    /**
     * 批量根据主键 ID 查找
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.id IN :ids")
    List<QuestionJpaEntity> findByIds(@Param("ids") List<Long> ids);

    /**
     * 查找用户的题目（分页）
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.userId = :userId ORDER BY q.createdAt DESC LIMIT :limit OFFSET :offset")
    List<QuestionJpaEntity> findByUserIdPaginated(@Param("userId") String userId, @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 查找公共题目（分页）
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.visibility = 'PUBLIC' ORDER BY q.createdAt DESC LIMIT :limit OFFSET :offset")
    List<QuestionJpaEntity> findPublicQuestionsPaginated(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * 统计用户指定可见性的题目数量
     */
    @Query("SELECT COUNT(q) FROM QuestionJpaEntity q WHERE q.userId = :userId AND q.visibility = :visibility")
    long countByUserIdAndVisibility(@Param("userId") String userId, @Param("visibility") Visibility visibility);

    /**
     * 查找用户的私有题目（用于模糊搜索）
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.userId = :userId AND q.visibility = 'PRIVATE' AND LOWER(q.questionText) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY q.createdAt DESC")
    List<QuestionJpaEntity> searchPrivateByKeyword(@Param("userId") String userId, @Param("keyword") String keyword);

    /**
     * 更新可见性为公共（发布到公共题库）
     */
    @Modifying
    @Query("UPDATE QuestionJpaEntity q SET q.visibility = 'PUBLIC', q.updatedAt = CURRENT_TIMESTAMP WHERE q.id = :id AND q.userId = :userId")
    int updateVisibilityToPublic(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 删除题目（验证所有权）
     */
    @Modifying
    @Query("DELETE FROM QuestionJpaEntity q WHERE q.id = :id AND q.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    /**
     * 查找用户可见的指定公司题目
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.company = :company AND (q.visibility = 'PUBLIC' OR q.userId = :userId) ORDER BY q.createdAt DESC")
    List<QuestionJpaEntity> findByCompanyForUser(@Param("userId") String userId, @Param("company") String company);

    /**
     * 查找未生成答案的题目（用于后台 Answer Worker 轮询）
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.answer IS NULL OR q.answer = '' ORDER BY q.createdAt ASC LIMIT :limit")
    List<QuestionJpaEntity> findUnansweredQuestions(@Param("limit") int limit);

    /**
     * 查找最近更新过的题目（用于 Reembed Worker）
     */
    @Query("SELECT q FROM QuestionJpaEntity q WHERE q.updatedAt > :since ORDER BY q.updatedAt ASC LIMIT :limit")
    List<QuestionJpaEntity> findRecentlyUpdated(@Param("since") java.time.LocalDateTime since, @Param("limit") int limit);
}
