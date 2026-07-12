package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.common.BusinessException;
import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 百炼 DashScope Rerank 精排服务实现
 *
 * <p>调用 DashScope Text Rerank API 对候选文档重新打分排序，
 * 使用 qwen3-rerank 模型，提升检索精度。</p>
 *
 * <p>Rerank 放在 RRF 融合之后、MMR 多样化之前：<br>
 * RRF融合 → Rerank精排 → MMR多样化 → 阈值过滤</p>
 */
@Slf4j
@Service
public class RerankServiceImpl implements RerankService {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestTemplate restTemplate;
    private final RagConfig ragConfig;

    public RerankServiceImpl(RestTemplateBuilder builder, RagConfig ragConfig) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.ragConfig = ragConfig;
    }

    @Override
    public List<LawKnowledge> rerank(String query, List<LawKnowledge> candidates, int maxInput, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<LawKnowledge> inputList = candidates;
        if (inputList.size() > maxInput) {
            inputList = inputList.subList(0, maxInput);
        }

        List<String> documents = new ArrayList<>();
        for (LawKnowledge k : inputList) {
            documents.add(buildDocumentText(k));
        }

        try {
            Map<String, Object> response = callRerankApi(query, documents, topN);
            List<LawKnowledge> reranked = applyRerankResults(inputList, response);
            log.info("Rerank 完成: candidates={} input={} output={}", candidates.size(), inputList.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank API 调用失败，降级使用原始排序: {}", e.getMessage());
            return inputList.size() > topN ? new ArrayList<>(inputList.subList(0, topN)) : new ArrayList<>(inputList);
        }
    }

    private String buildDocumentText(LawKnowledge k) {
        StringBuilder sb = new StringBuilder();
        if (k.getTitle() != null && !k.getTitle().isBlank()) {
            sb.append(k.getTitle());
        }
        if (k.getChapter() != null && !k.getChapter().isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(k.getChapter());
        }
        if (k.getContent() != null && !k.getContent().isBlank()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(k.getContent());
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callRerankApi(String query, List<String> documents, int topN) {
        String apiKey = ragConfig.getRerankApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("rag.search.rerank.api-key 未配置");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", ragConfig.getRerankModel(),
                "input", Map.of(
                        "query", query,
                        "documents", documents
                ),
                "parameters", Map.of(
                        "top_n", topN,
                        "return_documents", false
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        log.debug("调用 Rerank API: model={} queryLen={} docCount={} topN={}",
                ragConfig.getRerankModel(), query.length(), documents.size(), topN);

        long t0 = System.currentTimeMillis();
        ResponseEntity<Map> response = restTemplate.postForEntity(RERANK_URL, request, Map.class);
        long elapsed = System.currentTimeMillis() - t0;

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw BusinessException.serviceError("Rerank API 调用失败");
        }

        Map<String, Object> body2 = response.getBody();
        log.info("Rerank API 响应: status={} elapsedMs={}", response.getStatusCode(), elapsed);
        return body2;
    }

    @SuppressWarnings("unchecked")
    private List<LawKnowledge> applyRerankResults(List<LawKnowledge> inputList, Map<String, Object> response) {
        Map<String, Object> output = (Map<String, Object>) response.get("output");
        if (output == null) {
            log.warn("Rerank 响应缺少 output 字段，返回原始 top-N");
            return new ArrayList<>(inputList);
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
        if (results == null || results.isEmpty()) {
            log.warn("Rerank 响应 results 为空");
            return new ArrayList<>(inputList);
        }

        List<LawKnowledge> reranked = new ArrayList<>();
        for (Map<String, Object> result : results) {
            Number indexNum = (Number) result.get("index");
            Number score = (Number) result.get("relevance_score");
            if (indexNum == null) continue;
            int idx = indexNum.intValue();
            if (idx >= 0 && idx < inputList.size()) {
                LawKnowledge k = inputList.get(idx);
                k.setScore(score != null ? score.doubleValue() : k.getScore());
                reranked.add(k);
            }
        }

        if (reranked.isEmpty()) {
            return new ArrayList<>(inputList);
        }

        return reranked;
    }
}
