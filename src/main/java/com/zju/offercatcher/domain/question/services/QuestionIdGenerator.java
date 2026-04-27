package com.zju.offercatcher.domain.question.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 题目 ID 生成器
 * 使用 MD5 算法生成唯一 ID，格式：MD5(userId|company|questionText)
 *
 * ID 包含 userId 确保用户隔离：
 * - 不同用户上传相同题目会生成不同 ID
 * - 系统导入题目使用 userId = "system"
 */
public final class QuestionIdGenerator {

    private static final String ALGORITHM = "MD5";

    /**
     * 生成题目唯一 ID
     * @param userId 用户 ID（用于用户隔离）
     * @param company 公司名称
     * @param questionText 题目文本
     * @return 32 位 MD5 字符串
     */
    public static String generate(String userId, String company, String questionText) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (questionText == null || questionText.isBlank()) {
            throw new IllegalArgumentException("questionText cannot be null or blank");
        }

        // 构建 ID 输入字符串：userId|company|questionText
        // company 可为空，但 userId 必须存在确保用户隔离
        String input = userId + "|" + (company != null ? company : "") + "|" + questionText.trim();

        return md5Hash(input);
    }

    /**
     * 生成系统题目 ID（userId = "system")
     * @param company 公司名称
     * @param questionText 题目文本
     * @return 32 位 MD5 字符串
     */
    public static String generateSystemId(String company, String questionText) {
        return generate("system", company, questionText);
    }

    private static String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}