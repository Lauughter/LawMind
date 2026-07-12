package com.lhs.lawmind.agent.memory;

import com.lhs.lawmind.utils.EmbeddingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryRetriever 两级检索测试")
class MemoryRetrieverTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private EmbeddingUtil embeddingUtil;

    private MemoryConfig memoryConfig;
    private MemoryRetriever memoryRetriever;

    @BeforeEach
    void setUp() {
        memoryConfig = new MemoryConfig();
        memoryRetriever = new MemoryRetriever(memoryStore, embeddingUtil, memoryConfig);
    }

    @Test
    @DisplayName("一级索引格式化正确按类型分组")
    void formatIndexForPrompt_shouldGroupByType() {
        AiMemory user1 = createMemory(1L, MemoryType.USER, "HR经理", "法律知识中等", 0.9);
        AiMemory proj1 = createMemory(2L, MemoryType.PROJECT, "审查了劳动合同", "发现竞业限制问题", 0.8);
        AiMemory feed1 = createMemory(3L, MemoryType.FEEDBACK, "纠正：试用期按第47条", "用户指正了之前的错误", 0.9);

        String result = memoryRetriever.formatIndexForPrompt(List.of(user1, proj1, feed1));

        assertThat(result).contains("用户画像 (USER)");
        assertThat(result).contains("近期事项 (PROJECT)");
        assertThat(result).contains("历史反馈 (FEEDBACK)");
        assertThat(result).contains("[M1]").contains("[M2]").contains("[M3]");
        assertThat(result).contains("retrieveMemory");
    }

    @Test
    @DisplayName("高重要性记忆标注星号")
    void formatIndexForPrompt_shouldMarkHighImportance() {
        AiMemory important = createMemory(1L, MemoryType.PROJECT, "重大风险合同", "重要", 0.9);
        AiMemory normal = createMemory(2L, MemoryType.PROJECT, "简单咨询", "不重要", 0.3);

        String result = memoryRetriever.formatIndexForPrompt(List.of(important, normal));

        assertThat(result).contains("★");
    }

    @Test
    @DisplayName("空记忆列表返回空字符串")
    void formatIndexForPrompt_shouldReturnEmptyForNull() {
        assertThat(memoryRetriever.formatIndexForPrompt(null)).isEmpty();
        assertThat(memoryRetriever.formatIndexForPrompt(List.of())).isEmpty();
    }

    @Test
    @DisplayName("二级详情格式化按类型分组并显示摘要")
    void formatDetailForPrompt_shouldShowSummaryPreference() {
        AiMemory mem = createMemory(1L, MemoryType.USER, "用户偏好", "详细正文很长很长...", 0.7);
        mem.setSummary("简短摘要版本");

        String result = memoryRetriever.formatDetailForPrompt(List.of(mem));

        assertThat(result).contains("简短摘要版本");
        assertThat(result).contains("用户画像");
    }

    @Test
    @DisplayName("retrieve 返回 MemoryContext 包含索引和详情")
    void retrieve_shouldReturnContext() throws Exception {
        AiMemory mem = createMemory(1L, MemoryType.PROJECT, "审查合同", "详情", 0.8);
        when(memoryStore.findTopByImportance(anyLong(), anyInt())).thenReturn(List.of(mem));
        when(embeddingUtil.embed(anyString())).thenReturn(new float[1536]);
        when(memoryStore.searchByVector(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(1L));
        when(memoryStore.findById(1L)).thenReturn(mem);

        MemoryRetriever.MemoryContext ctx = memoryRetriever.retrieve(1L, "上次那个合同");

        assertThat(ctx.indexMemories()).hasSize(1);
        assertThat(ctx.detailMemories()).hasSize(1);
        assertThat(ctx.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("向量检索为空时返回空条目不崩溃")
    void retrieve_shouldHandleEmptyVectorSearch() throws Exception {
        AiMemory mem = createMemory(1L, MemoryType.PROJECT, "测试", "详情", 0.5);
        when(memoryStore.findTopByImportance(anyLong(), anyInt())).thenReturn(List.of(mem));
        when(embeddingUtil.embed(anyString())).thenReturn(new float[0]);

        MemoryRetriever.MemoryContext ctx = memoryRetriever.retrieve(1L, "测试问题");

        assertThat(ctx.indexMemories()).hasSize(1);
        assertThat(ctx.detailMemories()).isEmpty();
    }

    private AiMemory createMemory(Long id, MemoryType type, String title, String body, double importance) {
        AiMemory m = new AiMemory();
        m.setId(id);
        m.setUserId(1L);
        m.setType(type);
        m.setTitle(title);
        m.setBody(body);
        m.setImportance(importance);
        m.setConfidence(0.5);
        m.setAccessCount(0);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }
}
