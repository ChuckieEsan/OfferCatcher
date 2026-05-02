package com.zju.offercatcher.application.service;

import com.zju.offercatcher.application.agent.AnswerSpecialistAgent;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionApplicationServiceTest {

    @Mock QuestionRepository questionRepository;
    @Mock CacheApplicationService cacheService;
    @Mock AnswerSpecialistAgent answerAgent;
    @InjectMocks QuestionApplicationService service;

    Question sample = Question.createPrivate("user-1", "HashMap 原理？", "阿里", "Java",
        QuestionType.KNOWLEDGE, List.of("HashMap"));

    @Nested
    @DisplayName("createQuestion")
    class Create {

        @Test
        @DisplayName("创建题目并保存到 Repository")
        void createAndSave() {
            Question result = service.createQuestion("user-1", "HashMap 原理？", "阿里", "Java",
                QuestionType.KNOWLEDGE, List.of("HashMap"));
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("user-1");
            verify(questionRepository).save(any(Question.class));
        }
    }

    @Nested
    @DisplayName("getQuestion")
    class Get {

        @Test
        @DisplayName("找到题目返回 Optional")
        void found() {
            when(questionRepository.findById(1L)).thenReturn(Optional.of(sample));
            assertThat(service.getQuestion(1L)).isPresent();
        }

        @Test
        @DisplayName("未找到返回空 Optional")
        void notFound() {
            when(questionRepository.findById(999L)).thenReturn(Optional.empty());
            assertThat(service.getQuestion(999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateQuestion")
    class Update {

        @Test
        @DisplayName("更新答案并保存")
        void updateAnswer() {
            when(questionRepository.findById(1L)).thenReturn(Optional.of(sample));
            Optional<Question> result = service.updateQuestion(1L, "新答案", null, null, null);
            assertThat(result).isPresent();
            assertThat(result.get().getAnswer()).isEqualTo("新答案");
            verify(questionRepository).save(any(Question.class));
        }

        @Test
        @DisplayName("题目不存在返回空")
        void notFound() {
            when(questionRepository.findById(999L)).thenReturn(Optional.empty());
            assertThat(service.updateQuestion(999L, "ans", null, null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteQuestion")
    class Delete {

        @Test
        @DisplayName("删除成功返回 true")
        void deleteSuccess() {
            when(questionRepository.findById(1L)).thenReturn(Optional.of(sample));
            assertThat(service.deleteQuestion(1L, "user-1")).isTrue();
            verify(questionRepository).deleteById(1L, "user-1");
        }

        @Test
        @DisplayName("题目不存在返回 false")
        void notFound() {
            when(questionRepository.findById(999L)).thenReturn(Optional.empty());
            assertThat(service.deleteQuestion(999L, "user-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("listQuestions")
    class ListQuestions {

        @Test
        @DisplayName("无过滤条件返回所有题目")
        void noFilters() {
            java.util.List<Question> mockList = java.util.List.of(sample);
            when(questionRepository.findByUserId("user-1", 1, 20)).thenReturn(mockList);
            java.util.List<Question> result = service.listQuestions("user-1", null, null, null, null, null, null, 1, 20);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("按公司过滤")
        void filterByCompany() {
            Question ali = Question.createPrivate("user-1", "Q1", "阿里", "Java", QuestionType.KNOWLEDGE, java.util.List.of());
            Question bytedance = Question.createPrivate("user-1", "Q2", "字节", "Java", QuestionType.KNOWLEDGE, java.util.List.of());
            java.util.List<Question> mockList = java.util.List.of(ali, bytedance);
            when(questionRepository.findByUserId("user-1", 1, 20)).thenReturn(mockList);

            java.util.List<Question> result = service.listQuestions("user-1", "阿里", null, null, null, null, null, 1, 20);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getCompany()).isEqualTo("阿里");
        }
    }
}
