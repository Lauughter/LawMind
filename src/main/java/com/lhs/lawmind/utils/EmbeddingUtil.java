package com.lhs.lawmind.utils;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class EmbeddingUtil {

    /** DashScope text-embedding API 单次最大输入条数 */
    private static final int MAX_BATCH_SIZE = 25;

    private final EmbeddingModel embeddingModel;

    public EmbeddingUtil(Optional<EmbeddingModel> embeddingModel) {
        this.embeddingModel = embeddingModel.orElse(null);
    }

    /**
     * 将文本转换为向量
     * @param text 文本
     * @return 向量
     */
    public float[] embed(String text) {
        if (embeddingModel == null) {
            log.warn("EmbeddingModel 未初始化");
            return new float[0];
        }
        if (text == null || text.trim().isEmpty()) {
            log.warn("文本为空，无法进行向量化");
            return new float[0];
        }
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            float[] vector = embedding.vector();
            if (vector == null || vector.length == 0) {
                log.warn("向量化结果为空向量");
                return new float[0];
            }
            log.debug("文本向量化成功，向量长度：{}", vector.length);
            return vector;
        } catch (Exception e) {
            log.error("文本向量化失败: {}", e.getMessage());
            return new float[0];
        }
    }

    /**
     * 批量将文本转换为向量（使用 Embedding API 单次批量调用）
     * <p>自动按 MAX_BATCH_SIZE 拆分子批次，避免单次请求过大</p>
     * @param texts 文本列表
     * @return 向量列表，与输入顺序一一对应
     */
    public float[][] embedBatch(List<String> texts) {
        if (embeddingModel == null) {
            log.warn("EmbeddingModel 未初始化");
            return new float[0][0];
        }
        if (texts == null || texts.isEmpty()) {
            log.warn("文本列表为空，无法进行批量向量化");
            return new float[0][0];
        }

        float[][] allVectors = new float[texts.size()][];
        int totalSubBatches = (texts.size() + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;

        for (int batchIdx = 0; batchIdx < totalSubBatches; batchIdx++) {
            int from = batchIdx * MAX_BATCH_SIZE;
            int to = Math.min(from + MAX_BATCH_SIZE, texts.size());
            List<String> subBatch = texts.subList(from, to);

            try {
                List<TextSegment> segments = new ArrayList<>(subBatch.size());
                for (String text : subBatch) {
                    segments.add(TextSegment.from(text));
                }

                Response<List<Embedding>> response = embeddingModel.embedAll(segments);
                List<Embedding> embeddings = response.content();

                for (int i = 0; i < embeddings.size(); i++) {
                    float[] vector = embeddings.get(i).vector();
                    allVectors[from + i] = (vector != null && vector.length > 0) ? vector : new float[0];
                }
            } catch (Exception e) {
                log.error("批量向量化子批次失败: batch={}/{} size={} error={}",
                        batchIdx + 1, totalSubBatches, subBatch.size(), e.getMessage());
                // 失败的子批次逐条降级处理
                for (int i = 0; i < subBatch.size(); i++) {
                    allVectors[from + i] = embed(subBatch.get(i));
                }
            }
        }

        log.debug("批量向量化完成: texts={} subBatches={}", texts.size(), totalSubBatches);
        return allVectors;
    }

    /**
     * 批量将文本转换为向量（可变参数便捷方法，内部委托给 {@link #embedBatch(List)}）
     * @deprecated 建议使用 {@link #embedBatch(List)} 获得真正的批量调用
     */
    @Deprecated
    public float[][] embedBatch(String... texts) {
        if (texts == null || texts.length == 0) {
            return new float[0][0];
        }
        List<String> list = new ArrayList<>(texts.length);
        for (String t : texts) {
            list.add(t);
        }
        return embedBatch(list);
    }
}
