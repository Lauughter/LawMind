package com.lhs.lawmind.agent.tool;

import com.lhs.lawmind.entity.SimilarQuestion;
import com.lhs.lawmind.service.SimilarQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LawVerificationToolsTest {

    @Mock
    private SimilarQuestionService similarQuestionService;

    private LawVerificationTools lawVerificationTools;

    @BeforeEach
    void setUp() {
        lawVerificationTools = new LawVerificationTools(similarQuestionService);
    }

    @Test
    void searchSimilarQuestions_shouldReturnMatch_whenFound() {
        SimilarQuestion sq = new SimilarQuestion();
        sq.setQuestion("被公司开除能赔多少钱");
        sq.setAnswer("根据《劳动合同法》第47条...");
        sq.setVisitCount(15);
        sq.setKnowledgeIds("1,2,3");

        when(similarQuestionService.searchSimilarQuestion("被公司开除怎么办"))
                .thenReturn(sq);

        String result = lawVerificationTools.searchSimilarQuestions("被公司开除怎么办");

        assertThat(result).contains("[相似问题匹配]");
        assertThat(result).contains("被公司开除能赔多少钱");
        assertThat(result).contains("劳动合同法");
        assertThat(result).contains("15");
        assertThat(result).contains("1,2,3");
        assertThat(result).contains("核实");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void searchSimilarQuestions_shouldReturnNotFound_whenNoMatch() {
        when(similarQuestionService.searchSimilarQuestion(anyString()))
                .thenReturn(null);

        String result = lawVerificationTools.searchSimilarQuestions("新法律问题");

        assertThat(result).contains("[相似问题]");
        assertThat(result).contains("未找到");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void searchSimilarQuestions_shouldHandleNullVisitCount() {
        SimilarQuestion sq = new SimilarQuestion();
        sq.setQuestion("测试问题");
        sq.setAnswer("测试回答");
        sq.setVisitCount(null);

        when(similarQuestionService.searchSimilarQuestion(anyString()))
                .thenReturn(sq);

        String result = lawVerificationTools.searchSimilarQuestions("测试问题");

        assertThat(result).contains("访问次数：0");
    }

    @Test
    void searchSimilarQuestions_shouldReturnError_whenServiceFails() {
        when(similarQuestionService.searchSimilarQuestion(anyString()))
                .thenThrow(new RuntimeException("Redis连接失败"));

        String result = lawVerificationTools.searchSimilarQuestions("测试");

        assertThat(result).startsWith("[Tool 错误]");
        assertThat(result).contains("相似问题检索失败");
    }

    @Test
    void verifyCitation_shouldPass_whenFoundInSource() {
        String citation = "根据《劳动合同法》第三十九条";
        String sourceText = "劳动合同法第三十九条规定了用人单位可以单方解除劳动合同的情形";

        String result = lawVerificationTools.verifyCitation(citation, sourceText);

        assertThat(result).contains("[引用校验] 通过");
        assertThat(result).contains(citation);
        assertThat(result).contains("可以输出");
    }

    @Test
    void verifyCitation_shouldFail_whenNotFoundInSource() {
        String citation = "根据《劳动合同法》第九十九条";
        String sourceText = "劳动合同法第三十九条规定了用人单位可以单方解除劳动合同的情形";

        String result = lawVerificationTools.verifyCitation(citation, sourceText);

        assertThat(result).contains("[引用校验] 未通过");
        assertThat(result).contains("未找到对应原文");
        assertThat(result).contains("标注\"待核实\"");
    }

    @Test
    void verifyCitation_shouldHandleEmptyCitation() {
        String result = lawVerificationTools.verifyCitation("", "some source text");

        assertThat(result).contains("[引用校验]");
        assertThat(result).contains("未提供");
    }

    @Test
    void verifyCitation_shouldHandleNullCitation() {
        String result = lawVerificationTools.verifyCitation(null, "some source text");

        assertThat(result).contains("[引用校验]");
        assertThat(result).contains("未提供");
    }

    @Test
    void verifyCitation_shouldHandleNullSourceText() {
        String result = lawVerificationTools.verifyCitation(
                "根据《劳动合同法》第三十九条", null);

        assertThat(result).contains("[引用校验] 未通过");
        assertThat(result).contains("未找到对应原文依据");
    }
}
