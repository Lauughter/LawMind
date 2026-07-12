package com.lhs.lawmind.agent.tool;

import com.lhs.lawmind.agent.memory.AiMemory;
import com.lhs.lawmind.agent.memory.MemoryRetriever;
import com.lhs.lawmind.agent.memory.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetrieveMemoryTool Agent 工具测试")
class RetrieveMemoryToolTest {

    @Mock
    private MemoryRetriever memoryRetriever;

    private RetrieveMemoryTool tool;

    @BeforeEach
    void setUp() {
        tool = new RetrieveMemoryTool(memoryRetriever);
    }

    @Test
    @DisplayName("retrieveMemory 返回记忆详情")
    void retrieveMemory_shouldReturnMemoryDetail() {
        AiMemory mem = new AiMemory();
        mem.setId(5L);
        mem.setType(MemoryType.FEEDBACK);
        mem.setTitle("用户纠正试用期上限");
        mem.setBody("用户指出试用期最长6个月，依据《劳动合同法》第19条");

        when(memoryRetriever.getMemoryDetail(5L)).thenReturn(mem);

        String result = tool.retrieveMemory("M5");

        assertThat(result).contains("用户纠正试用期上限");
        assertThat(result).contains("第19条");
        assertThat(result).contains("FEEDBACK");
    }

    @Test
    @DisplayName("retrieveMemory 记忆不存在时返回错误提示")
    void retrieveMemory_shouldReturnErrorWhenNotFound() {
        when(memoryRetriever.getMemoryDetail(anyLong())).thenReturn(null);

        String result = tool.retrieveMemory("M99");

        assertThat(result).contains("记忆不存在");
    }

    @Test
    @DisplayName("retrieveMemory 无效 ID 格式返回错误提示")
    void retrieveMemory_shouldReturnErrorForInvalidId() {
        String result = tool.retrieveMemory("ABC");

        assertThat(result).contains("无效的记忆 ID");
    }

    @Test
    @DisplayName("retrieveMemory 纯数字 ID 也可正常解析")
    void retrieveMemory_shouldHandleNumericId() {
        AiMemory mem = new AiMemory();
        mem.setId(3L);
        mem.setType(MemoryType.USER);
        mem.setTitle("用户偏好");
        mem.setBody("偏好表格形式");

        when(memoryRetriever.getMemoryDetail(3L)).thenReturn(mem);

        String result = tool.retrieveMemory("3");

        assertThat(result).contains("用户偏好");
    }
}
