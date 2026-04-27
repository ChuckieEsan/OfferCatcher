package com.zju.offercatcher.infrastructure.adapters.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.zju.offercatcher.infrastructure.config.EmbeddingProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

@Service
public class OnnxEmbeddingAdapter {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingAdapter.class);

    private final EmbeddingProperties properties;
    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    @Getter
    private boolean initialized = false;

    public OnnxEmbeddingAdapter(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            String modelPath = properties.getModelPath();
            Path modelFile = Path.of(modelPath, "onnx", "model.onnx");
            Path tokenizerFile = Path.of(modelPath, "tokenizer.json");
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelFile.toString(),
                new OrtSession.SessionOptions());
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toString());
            initialized = true;
            log.info("ONNX Embedding model loaded: {}, vectorSize={}", modelPath, properties.getVectorSize());
        } catch (Exception e) {
            log.warn("ONNX Embedding model not available, embedding disabled: {}", e.getMessage());
        }
    }

    public float[] embed(String text) {
        float[][] results = embedBatch(List.of(text));
        return results[0];
    }

    public float[][] embedBatch(List<String> texts) {
        if (!initialized) {
            throw new IllegalStateException("Embedding adapter is not initialized");
        }
        if (texts == null || texts.isEmpty()) {
            return new float[0][];
        }

        int batchSize = texts.size();
        int maxLen = 0;
        List<long[]> inputIdsList = new ArrayList<>();
        List<long[]> attentionMaskList = new ArrayList<>();

        for (String text : texts) {
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            inputIdsList.add(inputIds);
            attentionMaskList.add(attentionMask);
            maxLen = Math.max(maxLen, inputIds.length);
        }

        long[][] paddedInputIds = new long[batchSize][maxLen];
        long[][] paddedAttentionMask = new long[batchSize][maxLen];
        for (int i = 0; i < batchSize; i++) {
            long[] ids = inputIdsList.get(i);
            long[] mask = attentionMaskList.get(i);
            System.arraycopy(ids, 0, paddedInputIds[i], 0, ids.length);
            System.arraycopy(mask, 0, paddedAttentionMask[i], 0, mask.length);
        }

        try {
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, paddedInputIds);
                 OnnxTensor maskTensor = OnnxTensor.createTensor(env, paddedAttentionMask)) {

                Map<String, OnnxTensor> inputs = Map.of(
                    "input_ids", inputTensor,
                    "attention_mask", maskTensor
                );

                OrtSession.Result result = session.run(inputs);
                float[][][] output = (float[][][]) result.get(0).getValue();

                float[][] embeddings = new float[batchSize][];
                for (int i = 0; i < batchSize; i++) {
                    embeddings[i] = output[i][0];
                }
                result.close();
                return embeddings;
            }
        } catch (Exception e) {
            log.error("Embedding inference failed", e);
            throw new RuntimeException("Embedding inference failed", e);
        }
    }
}
