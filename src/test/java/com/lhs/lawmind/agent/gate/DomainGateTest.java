package com.lhs.lawmind.agent.gate;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DomainGate — 领域门控测试")
class DomainGateTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private DomainGate domainGate;

    @BeforeEach
    void setUp() {
        var config = new IntentGateConfig(
                true, // ruleOnly = true, 不调 LLM
                new IntentGateConfig.DomainConfig(
                        List.of("合同", "劳动", "工伤", "赔偿", "婚姻", "离婚", "继承",
                                "刑事", "犯罪", "诉讼", "仲裁", "法院", "律师", "法条",
                                "法律", "法规", "司法", "判决", "债务", "侵权", "违约"),
                        List.of("天气", "游戏", "电影", "音乐", "美食", "旅游", "运动",
                                "健身", "美妆", "宠物", "彩票", "赌博"),
                        ""
                ),
                null, null, null,
                java.util.Map.of()
        );
        domainGate = new DomainGate(config, chatLanguageModel);
    }

    @Test
    @DisplayName("明显法律问题 — 合同相关")
    void shouldPassLegalQuestion_contractKeyword() {
        DomainVerdict verdict = domainGate.judge("公司单方面解除劳动合同需要赔偿吗？");
        assertThat(verdict.isLegal()).isTrue();
        assertThat(verdict.confidence()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("明显法律问题 — 婚姻相关")
    void shouldPassLegalQuestion_marriageKeyword() {
        DomainVerdict verdict = domainGate.judge("离婚后孩子抚养权归谁？");
        assertThat(verdict.isLegal()).isTrue();
    }

    @Test
    @DisplayName("明显法律问题 — 刑事相关")
    void shouldPassLegalQuestion_criminalKeyword() {
        DomainVerdict verdict = domainGate.judge("盗窃罪的立案标准是什么？");
        assertThat(verdict.isLegal()).isTrue();
    }

    @Test
    @DisplayName("明显法律问题 — 书名号法条引用")
    void shouldPassLegalQuestion_bookTitlePattern() {
        DomainVerdict verdict = domainGate.judge("《劳动合同法》第47条怎么规定的？");
        assertThat(verdict.isLegal()).isTrue();
    }

    @Test
    @DisplayName("明显法律问题 — 第X条法条引用")
    void shouldPassLegalQuestion_articlePattern() {
        DomainVerdict verdict = domainGate.judge("请问民法典第一千零七十六条是什么内容？");
        assertThat(verdict.isLegal()).isTrue();
    }

    @Test
    @DisplayName("明显非法律问题 — 天气")
    void shouldRejectNonLegalQuestion_weather() {
        DomainVerdict verdict = domainGate.judge("今天天气怎么样？");
        assertThat(verdict.isLegal()).isFalse();
        assertThat(verdict.category()).isEqualTo("non_legal");
    }

    @Test
    @DisplayName("明显非法律问题 — 游戏")
    void shouldRejectNonLegalQuestion_game() {
        DomainVerdict verdict = domainGate.judge("推荐一款好玩的游戏？");
        assertThat(verdict.isLegal()).isFalse();
    }

    @Test
    @DisplayName("明显非法律问题 — 美食")
    void shouldRejectNonLegalQuestion_food() {
        DomainVerdict verdict = domainGate.judge("附近有什么美食推荐？");
        assertThat(verdict.isLegal()).isFalse();
    }

    @Test
    @DisplayName("明显非法律问题 — 宠物")
    void shouldRejectNonLegalQuestion_pet() {
        DomainVerdict verdict = domainGate.judge("怎么训练宠物狗？");
        assertThat(verdict.isLegal()).isFalse();
    }

    @Test
    @DisplayName("空输入 — 格式异常")
    void shouldRejectEmptyInput() {
        DomainVerdict verdict = domainGate.judge("");
        assertThat(verdict.isLegal()).isFalse();
        assertThat(verdict.category()).isEqualTo("malformed");
    }

    @Test
    @DisplayName("null输入 — 格式异常")
    void shouldRejectNullInput() {
        DomainVerdict verdict = domainGate.judge(null);
        assertThat(verdict.isLegal()).isFalse();
    }

    @Test
    @DisplayName("纯符号 — 格式异常")
    void shouldRejectMalformedInput_symbols() {
        DomainVerdict verdict = domainGate.judge("???!!");
        assertThat(verdict.isLegal()).isFalse();
        assertThat(verdict.category()).isEqualTo("malformed");
    }

    @Test
    @DisplayName("敏感内容 — 拒绝")
    void shouldRejectSensitiveContent() {
        DomainVerdict verdict = domainGate.judge("推荐一个翻墙VPN");
        assertThat(verdict.isLegal()).isFalse();
        assertThat(verdict.category()).isEqualTo("sensitive");
    }

    @Test
    @DisplayName("法律相关问题但无明确关键词 — 纯规则模式默认放行")
    void shouldPassRuleOnlyMode_ambiguous() {
        DomainVerdict verdict = domainGate.judge("我被欺负了怎么办？");
        // ruleOnly=true 时，模糊边界默认放行
        assertThat(verdict.isLegal()).isTrue();
    }
}
