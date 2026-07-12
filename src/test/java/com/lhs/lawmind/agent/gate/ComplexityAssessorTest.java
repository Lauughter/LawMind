package com.lhs.lawmind.agent.gate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ComplexityAssessor — 复杂度评估测试")
class ComplexityAssessorTest {

    private ComplexityAssessor assessor;

    @BeforeEach
    void setUp() {
        var config = new IntentGateConfig(
                true,
                null,
                null,
                new IntentGateConfig.ComplexityConfig(
                        0.4, 0.2, 0.2, 0.2,
                        0.35, 0.65,
                        List.of("刑法", "刑事", "知识产权", "专利", "商标", "侵权",
                                "破产", "重整", "反垄断", "证券", "信托", "公司",
                                "股权", "跨国", "涉外"),
                        List.of("怎么起诉", "起诉流程", "仲裁流程", "诉讼程序",
                                "怎么申请", "需要什么材料", "去哪里", "管辖权",
                                "时效", "上诉", "再审", "申诉", "强制执行")
                ),
                null,
                Map.of()
        );
        assessor = new ComplexityAssessor(config);
    }

    @Test
    @DisplayName("简单问题 — 单句法条查询")
    void shouldReturnSimple_forSingleClause() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess("劳动合同法第47条是什么？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.SIMPLE);
    }

    @Test
    @DisplayName("简单问题 — 法律概念查询")
    void shouldReturnSimple_forConceptQuestion() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess("什么是无因管理？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.SIMPLE);
    }

    @Test
    @DisplayName("中等复杂度 — 多句问题含计算")
    void shouldReturnMedium_forMultiClauseWithCalc() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess(
                "我被公司违法辞退了。工作3年了。请问能获得多少赔偿？需要准备哪些证据？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.MEDIUM);
    }

    @Test
    @DisplayName("中等复杂度 — 含计算和程序")
    void shouldReturnMedium_forCalcWithProcedure() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess(
                "月工资12000元，工作5年，公司无故辞退。应该赔偿多少？需要准备什么材料去仲裁？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.MEDIUM);
    }

    @Test
    @DisplayName("复杂问题 — 跨国 + 证券 + 程序")
    void shouldReturnComplex_forComplexQuestion() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess(
                "我在一家跨国公司工作，持有股权激励。公司涉嫌证券欺诈，我想起诉。"
                        + "请问应该去哪里起诉？诉讼流程是什么？需要准备什么材料？诉讼时效是多久？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.COMPLEX);
    }

    @Test
    @DisplayName("复杂问题 — 破产重整 + 跨国 + 多程序")
    void shouldReturnComplex_forBankruptcyScenario() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess(
                "跨国公司在华子公司破产重整，涉外债权人如何申报债权？"
                        + "需要准备什么材料？去哪里申报？申报时效是多久？"
                        + "如果超过时效还能起诉吗？诉讼程序是什么？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.COMPLEX);
    }

    @Test
    @DisplayName("复杂问题 — 知识产权侵权 + 诉讼程序")
    void shouldReturnComplex_forIpWithProcedure() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess(
                "商标和专利被侵权了，怎么起诉到法院？管辖权如何确定？"
                        + "诉讼时效是多久？需要准备哪些证据材料？"
                        + "上诉流程是什么？强制执行怎么申请？");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.COMPLEX);
    }

    @Test
    @DisplayName("空输入 — SIMPLE")
    void shouldReturnSimple_forEmptyInput() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess("");
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.SIMPLE);
    }

    @Test
    @DisplayName("null输入 — SIMPLE")
    void shouldReturnSimple_forNullInput() {
        ComplexityAssessor.ComplexityLevel level = assessor.assess(null);
        assertThat(level).isEqualTo(ComplexityAssessor.ComplexityLevel.SIMPLE);
    }
}
