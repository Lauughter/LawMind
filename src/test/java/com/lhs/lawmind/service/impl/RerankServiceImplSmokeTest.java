package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.LawKnowledge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rerank 模型接入冒烟测试
 *
 * <p>直接调用百炼 DashScope Rerank API，验证 qwen3-rerank 模型可用性。
 * 需要设置环境变量 DASHSCOPE_API_KEY。</p>
 */
@DisplayName("Rerank 模型接入测试")
class RerankServiceImplSmokeTest {

    @Test
    @DisplayName("法律文档精排 — API 连通性验证")
    void rerankLegalDocuments_returnsSortedByRelevance() {
        // DASHSCOPE_API_KEY 未设置时自动跳过
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "跳过：DASHSCOPE_API_KEY 环境变量未设置");

        // 构造模拟 RagConfig
        RagConfig config = mock(RagConfig.class);
        when(config.getRerankModel()).thenReturn("qwen3-rerank");

        // 构造 RerankServiceImpl（手动注入）
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10));
        RerankServiceImpl service = new RerankServiceImpl(builder, config);

        // 准备测试数据：模拟法律知识库检索结果
        List<LawKnowledge> candidates = new ArrayList<>();

        LawKnowledge k1 = new LawKnowledge();
        k1.setId(1L);
        k1.setTitle("劳动法");
        k1.setChapter("第四章 工作时间和休息休假");
        k1.setContent("国家实行劳动者每日工作时间不超过八小时、平均每周工作时间不超过四十四小时的工时制度。");
        candidates.add(k1);

        LawKnowledge k2 = new LawKnowledge();
        k2.setId(2L);
        k2.setTitle("劳动法");
        k2.setChapter("第四章 工作时间和休息休假");
        k2.setContent("用人单位由于生产经营需要，经与工会和劳动者协商后可以延长工作时间，一般每日不得超过一小时；因特殊原因需要延长工作时间的，在保障劳动者身体健康的条件下延长工作时间每日不得超过三小时，但是每月不得超过三十六小时。");
        candidates.add(k2);

        LawKnowledge k3 = new LawKnowledge();
        k3.setId(3L);
        k3.setTitle("劳动合同法");
        k3.setChapter("第二章 劳动合同的订立");
        k3.setContent("建立劳动关系，应当订立书面劳动合同。已建立劳动关系，未同时订立书面劳动合同的，应当自用工之日起一个月内订立书面劳动合同。");
        candidates.add(k3);

        LawKnowledge k4 = new LawKnowledge();
        k4.setId(4L);
        k4.setTitle("劳动法");
        k4.setChapter("第四章 工资");
        k4.setContent("工资应当以货币形式按月支付给劳动者本人。不得克扣或者无故拖欠劳动者的工资。");
        candidates.add(k4);

        LawKnowledge k5 = new LawKnowledge();
        k5.setId(5L);
        k5.setTitle("劳动法");
        k5.setChapter("第四章 工作时间和休息休假");
        k5.setContent("用人单位安排劳动者延长工作时间的，支付不低于工资的百分之一百五十的工资报酬；休息日安排劳动者工作又不能安排补休的，支付不低于工资的百分之二百的工资报酬；法定休假日安排劳动者工作的，支付不低于工资的百分之三百的工资报酬。");
        candidates.add(k5);

        String query = "加班不给加班费怎么办";

        // 执行精排
        System.out.println("\n========== Rerank 模型测试 ==========");
        System.out.println("Query: " + query);
        System.out.println("Candidates: " + candidates.size() + " 条法律文档");
        System.out.println("Model: qwen3-rerank");
        System.out.println("======================================\n");

        long t0 = System.currentTimeMillis();
        List<LawKnowledge> result = service.rerank(query, candidates, 5, 5);
        long elapsed = System.currentTimeMillis() - t0;

        // 输出结果
        assertThat(result).isNotEmpty();
        System.out.println("精排结果（" + result.size() + " 条，耗时 " + elapsed + "ms）：");
        System.out.println("──────────────────────────────────────");
        for (int i = 0; i < result.size(); i++) {
            LawKnowledge k = result.get(i);
            System.out.printf("[%d] score=%.4f | %s - %s%n",
                    i + 1,
                    k.getScore() != null ? k.getScore() : -1.0,
                    k.getTitle(),
                    k.getChapter());
            if (k.getContent() != null && k.getContent().length() > 80) {
                System.out.println("    " + k.getContent().substring(0, 80) + "...");
            } else {
                System.out.println("    " + k.getContent());
            }
        }
        System.out.println("──────────────────────────────────────");

        // 验证：最相关的应该是第5条（加班费标准），最不相关的应该是第3条（劳动合同订立）
        LawKnowledge top = result.get(0);
        assertThat(top.getId()).as("最相关的应该是加班费条款(id=5)").isEqualTo(5L);

        LawKnowledge bottom = result.get(result.size() - 1);
        assertThat(bottom.getId()).as("最不相关的应该是劳动合同订立条款(id=3)").isEqualTo(3L);

        System.out.println("\n✓ Rerank 模型测试通过！精排正确将加班费条款排在最前、劳动合同条款排在最后");
    }
}
