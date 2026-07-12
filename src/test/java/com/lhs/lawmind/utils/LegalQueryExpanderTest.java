package com.lhs.lawmind.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalQueryExpanderTest {

    private final LegalQueryExpander expander = new LegalQueryExpander();

    @Test
    @DisplayName("退一赔三 → 法条原文关键词桥接")
    void shouldExpandTuiYiPeiSan() {
        String result = expander.expandQuery("买到翻新机能退一赔三吗");

        assertThat(result).contains("欺诈行为");
        assertThat(result).contains("增加赔偿");
        assertThat(result).contains("三倍");
        assertThat(result).contains("第五十五条");
    }

    @Test
    @DisplayName("翻新机 → 以次充好/以假充真/欺诈行为")
    void shouldExpandFanXinJi() {
        String result = expander.expandQuery("买到翻新机怎么办");

        assertThat(result).contains("以次充好");
        assertThat(result).contains("以假充真");
        assertThat(result).contains("欺诈行为");
    }

    @Test
    @DisplayName("展示机 → 商品说明/质量状况")
    void shouldExpandZhanShiJi() {
        String result = expander.expandQuery("商家说是展示机");

        assertThat(result).contains("商品说明");
        assertThat(result).contains("质量状况");
        assertThat(result).contains("第二十三条");
    }

    @Test
    @DisplayName("假一赔三 → 与退一赔三共享桥接")
    void shouldExpandJiaYiPeiSan() {
        String result = expander.expandQuery("可以假一赔三吗");

        assertThat(result).contains("欺诈行为");
        assertThat(result).contains("增加赔偿");
        assertThat(result).contains("三倍");
        assertThat(result).contains("五百元");
        assertThat(result).contains("第五十五条");
    }

    @Test
    @DisplayName("假一赔十 → 食品安全法惩罚性赔偿")
    void shouldExpandJiaYiPeiShi() {
        String result = expander.expandQuery("能假一赔十吗");

        assertThat(result).contains("惩罚性赔偿");
        assertThat(result).contains("食品安全法");
        assertThat(result).contains("第一百四十八条");
    }

    @Test
    @DisplayName("以次充好 → 扩展为欺诈+第五十六條")
    void shouldExpandYiCiChongHao() {
        String result = expander.expandQuery("商家以次充好");

        assertThat(result).contains("以假充真");
        assertThat(result).contains("欺诈行为");
        assertThat(result).contains("第五十五条");
        assertThat(result).contains("第五十六条");
    }

    @Test
    @DisplayName("N+1 → 经济补偿/代通知金")
    void shouldExpandNPlusOne() {
        String result = expander.expandQuery("被裁员能拿N+1吗");

        assertThat(result).contains("经济补偿");
        assertThat(result).contains("代通知金");
        assertThat(result).contains("第四十七条");
    }

    @Test
    @DisplayName("2N → 违法解除/赔偿金/二倍")
    void shouldExpandTwoN() {
        String result = expander.expandQuery("能要2N赔偿吗");

        assertThat(result).contains("违法解除");
        assertThat(result).contains("赔偿金");
        assertThat(result).contains("二倍");
        assertThat(result).contains("第八十七条");
    }

    @Test
    @DisplayName("净身出户 → 夫妻财产/过错方/损害赔偿")
    void shouldExpandJingShenChuHu() {
        String result = expander.expandQuery("出轨能让他净身出户吗");

        assertThat(result).contains("夫妻财产");
        assertThat(result).contains("过错方");
        assertThat(result).contains("损害赔偿");
        assertThat(result).contains("第一千零九十二条");
    }

    @Test
    @DisplayName("综合案例：京东翻新机退一赔三")
    void shouldExpandComplexQuery() {
        String query = "在京东买到翻新机，商家说是展示机不算翻新，能退一赔三吗";
        String result = expander.expandQuery(query);

        // 翻新机 → 以次充好/以假充真/欺诈行为/商品质量/第四十八条/第五十五条
        assertThat(result).contains("以次充好");
        assertThat(result).contains("以假充真");

        // 展示机 → 商品说明/质量状况/第二十三条/第四十八条
        assertThat(result).contains("商品说明");
        assertThat(result).contains("质量状况");

        // 退一赔三 → 欺诈行为/增加赔偿/三倍/五百元/第五十五条
        assertThat(result).contains("增加赔偿");
        assertThat(result).contains("三倍");
        assertThat(result).contains("五百元");
        assertThat(result).contains("第五十五条");

        // 买到 → 消费者权益保护法
        assertThat(result).contains("消费者权益保护法");

        // 去重验证：欺诈行为 应只出现一次
        long fraudCount = result.split("欺诈行为").length - 1;
        assertThat(fraudCount).isEqualTo(1);
    }

    @Test
    @DisplayName("空白/null输入原样返回")
    void shouldHandleEmpty() {
        assertThat(expander.expandQuery(null)).isNull();
        assertThat(expander.expandQuery("")).isEmpty();
        assertThat(expander.expandQuery("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("无匹配词时返回原问题")
    void shouldReturnOriginalWhenNoMatch() {
        String result = expander.expandQuery("今天天气怎么样");
        assertThat(result).isEqualTo("今天天气怎么样");
    }
}
