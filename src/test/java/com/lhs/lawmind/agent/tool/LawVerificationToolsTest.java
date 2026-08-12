package com.lhs.lawmind.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LawVerificationToolsTest {

    private LawVerificationTools lawVerificationTools;

    @BeforeEach
    void setUp() {
        lawVerificationTools = new LawVerificationTools();
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
