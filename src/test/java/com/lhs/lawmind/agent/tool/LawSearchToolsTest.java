package com.lhs.lawmind.agent.tool;

import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LawSearchToolsTest {

    @Mock
    private HybridSearchService hybridSearchService;
    @Mock
    private LawKnowledgeService lawKnowledgeService;
    @Mock
    private EmbeddingUtil embeddingUtil;

    private LawSearchTools lawSearchTools;

    @BeforeEach
    void setUp() {
        lawSearchTools = new LawSearchTools(
                hybridSearchService, lawKnowledgeService, embeddingUtil);
    }

    @Test
    void searchLawKnowledge_shouldReturnFormattedResults_whenResultsFound() {
        LawKnowledge k = new LawKnowledge();
        k.setLawType("劳动法");
        k.setTitle("《劳动合同法》第三十九条");
        k.setContent("劳动者有下列情形之一的，用人单位可以解除劳动合同...");
        k.setSource("法律知识库");

        when(embeddingUtil.embed(anyString())).thenReturn(new float[1536]);
        when(hybridSearchService.searchHybrid(any(), eq("劳动合同法 经济补偿"), eq(10)))
                .thenReturn(List.of(k));

        String result = lawSearchTools.searchLawKnowledge("劳动合同法 经济补偿", "");

        assertThat(result).contains("劳动法");
        assertThat(result).contains("劳动合同法");
        assertThat(result).contains("第三十九条");
        assertThat(result).contains("用人单位可以解除劳动合同");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void searchLawKnowledge_shouldReturnEmptyHint_whenNoResults() {
        when(embeddingUtil.embed(anyString())).thenReturn(new float[1536]);
        when(hybridSearchService.searchHybrid(any(), anyString(), eq(10)))
                .thenReturn(List.of());

        String result = lawSearchTools.searchLawKnowledge("不存在的法律问题", "");

        assertThat(result).contains("[检索结果]");
        assertThat(result).contains("未找到相关法律知识");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void searchLawKnowledge_shouldReturnError_whenEmbeddingFails() {
        when(embeddingUtil.embed(anyString()))
                .thenThrow(new RuntimeException("DashScope API连接超时"));

        String result = lawSearchTools.searchLawKnowledge("劳动法", "");

        assertThat(result).startsWith("[Tool 错误]");
        assertThat(result).contains("searchLawKnowledge 执行失败");
        assertThat(result).contains("DashScope API连接超时");
    }

    @Test
    void searchLawKnowledge_shouldFilterByLawType_whenLawTypeProvided() {
        LawKnowledge k = new LawKnowledge();
        k.setLawType("劳动法");
        k.setTitle("《劳动合同法》第四十七条");
        k.setContent("经济补偿按劳动者在本单位工作的年限...");
        k.setSource("法律知识库");

        when(embeddingUtil.embed(anyString())).thenReturn(new float[1536]);
        when(hybridSearchService.searchHybridFiltered(any(), anyString(), eq(10), eq("劳动法")))
                .thenReturn(List.of(k));

        String result = lawSearchTools.searchLawKnowledge("经济补偿", "劳动法");

        assertThat(result).contains("劳动法");
        assertThat(result).contains("第四十七条");
    }

    @Test
    void getArticleText_shouldReturnArticle_whenFound() {
        LawKnowledge k = new LawKnowledge();
        k.setTitle("《劳动合同法》第三十九条");
        k.setContent("劳动者有下列情形之一的，用人单位可以解除劳动合同...");

        when(lawKnowledgeService.search(eq("劳动合同法"), eq(1), eq(50)))
                .thenReturn(List.of(k));

        String result = lawSearchTools.getArticleText("劳动合同法", "第三十九条");

        assertThat(result).contains("《劳动合同法》第三十九条");
        assertThat(result).contains("用人单位可以解除劳动合同");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void getArticleText_shouldReturnNotFound_whenLawNotExist() {
        when(lawKnowledgeService.search(eq("不存在的法律"), eq(1), eq(50)))
                .thenReturn(List.of());

        String result = lawSearchTools.getArticleText("不存在的法律", "第一条");

        assertThat(result).contains("[查询结果]");
        assertThat(result).contains("未找到");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void getArticleText_shouldReturnArticleNotFound_whenArticleNumberNotFound() {
        LawKnowledge k = new LawKnowledge();
        k.setTitle("《劳动合同法》第四十七条");
        k.setContent("经济补偿按劳动者在本单位工作的年限...");

        when(lawKnowledgeService.search(eq("劳动合同法"), eq(1), eq(50)))
                .thenReturn(List.of(k));

        String result = lawSearchTools.getArticleText("劳动合同法", "第九十九条");

        assertThat(result).contains("[查询结果]");
        assertThat(result).contains("未找到第九十九条");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void getArticleText_shouldStripBookTitleMarks() {
        LawKnowledge k = new LawKnowledge();
        k.setTitle("《劳动合同法》第三十九条");
        k.setContent("...");

        when(lawKnowledgeService.search(eq("劳动合同法"), eq(1), eq(50)))
                .thenReturn(List.of(k));

        String result = lawSearchTools.getArticleText("《劳动合同法》", "第三十九条");

        assertThat(result).contains("《劳动合同法》第三十九条");
    }

    @Test
    void getArticleText_shouldHandleServiceException() {
        when(lawKnowledgeService.search(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        String result = lawSearchTools.getArticleText("劳动合同法", "第三十九条");

        assertThat(result).startsWith("[Tool 错误]");
        assertThat(result).contains("getArticleText 执行失败");
    }
}
