package com.lhs.lawmind.agent.tool;

import com.lhs.lawmind.utils.IntentClassifier;
import com.lhs.lawmind.utils.LegalQueryExpander;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LawIntentToolsTest {

    @Mock
    private IntentClassifier intentClassifier;
    @Mock
    private LegalQueryExpander legalQueryExpander;

    private LawIntentTools lawIntentTools;

    @BeforeEach
    void setUp() {
        lawIntentTools = new LawIntentTools(intentClassifier, legalQueryExpander);
    }

    @Test
    void classifyLegalIntent_shouldReturnArticleLookupInfo() {
        when(intentClassifier.classify("劳动合同法第三十九条是什么"))
                .thenReturn(IntentClassifier.Intent.ARTICLE_LOOKUP);
        when(intentClassifier.adjustTopK(IntentClassifier.Intent.ARTICLE_LOOKUP, 15))
                .thenReturn(20);
        when(intentClassifier.useDeepRetrieval(IntentClassifier.Intent.ARTICLE_LOOKUP))
                .thenReturn(true);

        String result = lawIntentTools.classifyLegalIntent("劳动合同法第三十九条是什么");

        assertThat(result).contains("[意图分析]");
        assertThat(result).contains("ARTICLE_LOOKUP");
        assertThat(result).contains("20");
        assertThat(result).contains("深度检索：是");
        assertThat(result).contains("精确匹配");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void classifyLegalIntent_shouldReturnCalculationInfo() {
        when(intentClassifier.classify("工伤赔偿多少钱"))
                .thenReturn(IntentClassifier.Intent.CALCULATION);
        when(intentClassifier.adjustTopK(IntentClassifier.Intent.CALCULATION, 15))
                .thenReturn(13);
        when(intentClassifier.useDeepRetrieval(IntentClassifier.Intent.CALCULATION))
                .thenReturn(false);

        String result = lawIntentTools.classifyLegalIntent("工伤赔偿多少钱");

        assertThat(result).contains("[意图分析]");
        assertThat(result).contains("CALCULATION");
        assertThat(result).contains("赔偿标准");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void classifyLegalIntent_shouldReturnDefaultConsultation() {
        when(intentClassifier.classify("我被公司开除了怎么办"))
                .thenReturn(IntentClassifier.Intent.LEGAL_CONSULTATION);
        when(intentClassifier.adjustTopK(IntentClassifier.Intent.LEGAL_CONSULTATION, 15))
                .thenReturn(15);
        when(intentClassifier.useDeepRetrieval(IntentClassifier.Intent.LEGAL_CONSULTATION))
                .thenReturn(false);

        String result = lawIntentTools.classifyLegalIntent("我被公司开除了怎么办");

        assertThat(result).contains("[意图分析]");
        assertThat(result).contains("LEGAL_CONSULTATION");
        assertThat(result).contains("完整RAG检索");
    }

    @Test
    void classifyLegalIntent_shouldReturnError_whenClassifierFails() {
        when(intentClassifier.classify(anyString()))
                .thenThrow(new RuntimeException("分类器内部错误"));

        String result = lawIntentTools.classifyLegalIntent("测试问题");

        assertThat(result).startsWith("[Tool 错误]");
        assertThat(result).contains("意图分类失败");
    }

    @Test
    void expandLegalQuery_shouldReturnExpandedQuery_whenMatchFound() {
        when(legalQueryExpander.expandQuery("被公司开除了怎么办"))
                .thenReturn("被公司开除了怎么办 解除劳动合同 辞退 用人单位单方解除");

        String result = lawIntentTools.expandLegalQuery("被公司开除了怎么办");

        assertThat(result).contains("[查询扩展结果]");
        assertThat(result).contains("被公司开除了怎么办");
        assertThat(result).contains("解除劳动合同");
        assertThat(result).contains("辞退");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void expandLegalQuery_shouldReturnNoMatchHint_whenNoMatchFound() {
        when(legalQueryExpander.expandQuery("今天天气怎么样"))
                .thenReturn("今天天气怎么样");

        String result = lawIntentTools.expandLegalQuery("今天天气怎么样");

        assertThat(result).contains("[查询扩展]");
        assertThat(result).contains("未匹配到");
    }

    @Test
    void expandLegalQuery_shouldReturnError_whenExpanderFails() {
        when(legalQueryExpander.expandQuery(anyString()))
                .thenThrow(new RuntimeException("扩展器内部错误"));

        String result = lawIntentTools.expandLegalQuery("测试查询");

        assertThat(result).startsWith("[Tool 错误]");
        assertThat(result).contains("查询扩展失败");
    }
}
