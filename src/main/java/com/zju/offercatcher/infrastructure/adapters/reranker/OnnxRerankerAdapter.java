package com.zju.offercatcher.infrastructure.adapters.reranker;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.zju.offercatcher.infrastructure.config.RerankerProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

@Service
public class OnnxRerankerAdapter {

    private static final Logger log = LoggerFactory.getLogger(OnnxRerankerAdapter.class);

    private final RerankerProperties properties;
    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    @Getter
    private boolean initialized = false;

    public OnnxRerankerAdapter(RerankerProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            String modelPath = properties.getModelPath();
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(Path.of(modelPath, "model.onnx").toString(),
                new OrtSession.SessionOptions());
            tokenizer = HuggingFaceTokenizer.newInstance(Path.of(modelPath, "tokenizer.json"));
            initialized = true;
            log.info("ONNX Reranker model loaded: {}", modelPath);
        } catch (Exception e) {
            log.warn("ONNX Reranker model not available, reranking disabled: {}", e.getMessage());
        }
    }

    public List<RankedResult> rerank(String query, List<String> candidates, int topK) {
        if (!initialized) {
            throw new IllegalStateException("Reranker adapter is not initialized");
        }
        List<Float> scores = computeScores(query, candidates);

        List<RankedResult> results = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            results.add(new RankedResult(i, scores.get(i)));
        }
        results.sort((a, b) -> Float.compare(b.score(), a.score()));

        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    public List<Float> computeScores(String query, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        int maxLen = properties.getMaxLength();
        List<Float> scores = new ArrayList<>();

        for (String candidate : candidates) {
            Encoding encoding = tokenizer.encode(query, candidate);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();

            int len = Math.min(inputIds.length, maxLen);
            long[][] batchInputIds = new long[1][len];
            long[][] batchAttentionMask = new long[1][len];
            System.arraycopy(inputIds, 0, batchInputIds[0], 0, len);
            System.arraycopy(attentionMask, 0, batchAttentionMask[0], 0, len);

            try {
                try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, batchInputIds);
                     OnnxTensor maskTensor = OnnxTensor.createTensor(env, batchAttentionMask)) {

                    Map<String, OnnxTensor> inputs = Map.of(
                        "input_ids", inputTensor,
                        "attention_mask", maskTensor
                    );

                    OrtSession.Result result = session.run(inputs);
                    float[][] output = (float[][]) result.get(0).getValue();
                    scores.add(output[0][0]);
                    result.close();
                }
            } catch (Exception e) {
                log.error("Reranker inference failed for candidate", e);
                scores.add(0.0f);
            }
        }

        return scores;
    }
}
