package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律实体抽取器
 * <p>作用是从用户输入的问题中抽取出结构化的法律实体信息，以便后续的检索和处理。</p>
 *
 * <p>从用户问题中抽取结构化法律实体，用于增强检索精度：</p>
 * <ul>
 *   <li>法律类型 (lawType) - 用于元数据过滤限定检索范围</li>
 *   <li>法条引用 (articleReference) - 如 "第39条"、"民法典第1046条"</li>
 *   <li>金额信息 (amountText) - 用于法律计算器路由</li>
 *   <li>当事人类型 (partyType) - 用人单位/劳动者/消费者/公司等</li>
 *   <li>时间信息 (timeRef) - N年/N个月/N天</li>
 * </ul>
 *
 * <p>纯规则引擎实现，零 LLM 调用延迟</p>
 */
@Slf4j
@Component
public class LegalEntityExtractor {

    /**
     * 法律类型 → 数据库 law_type 映射（关键词 → 数据库中的实际 law_type 值）
     * 注意：值必须与数据库 law_knowledge.law_type 字段中存储的完整法律名称匹配
     */
    private static final Map<String, String> LAW_TYPE_MAP = new LinkedHashMap<>();
    static {
        // 劳动法相关 → 中华人民共和国劳动法 / 中华人民共和国劳动合同法 / 中华人民共和国社会保险法
        LAW_TYPE_MAP.put("劳动", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("劳动合同", "中华人民共和国劳动合同法");
        LAW_TYPE_MAP.put("工伤", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("加班", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("辞退", "中华人民共和国劳动合同法");
        LAW_TYPE_MAP.put("开除", "中华人民共和国劳动合同法");
        LAW_TYPE_MAP.put("裁员", "中华人民共和国劳动合同法");
        LAW_TYPE_MAP.put("工资", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("社保", "中华人民共和国社会保险法");
        LAW_TYPE_MAP.put("社会保险", "中华人民共和国社会保险法");
        LAW_TYPE_MAP.put("五险一金", "中华人民共和国社会保险法");
        LAW_TYPE_MAP.put("职业病", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("伤残", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("试用期", "中华人民共和国劳动合同法");
        // 婚姻家庭 → 中华人民共和国民法 (民法典包含婚姻家庭编)
        LAW_TYPE_MAP.put("婚姻", "中华人民共和国民法");
        LAW_TYPE_MAP.put("离婚", "中华人民共和国民法");
        LAW_TYPE_MAP.put("结婚", "中华人民共和国民法");
        LAW_TYPE_MAP.put("夫妻", "中华人民共和国民法");
        LAW_TYPE_MAP.put("出轨", "中华人民共和国民法");
        LAW_TYPE_MAP.put("继承", "中华人民共和国民法");
        LAW_TYPE_MAP.put("赡养", "中华人民共和国民法");
        LAW_TYPE_MAP.put("抚养权", "中华人民共和国民法");
        LAW_TYPE_MAP.put("遗产", "中华人民共和国民法");
        // 合同/借贷 → 中华人民共和国民法
        LAW_TYPE_MAP.put("合同", "中华人民共和国民法");
        LAW_TYPE_MAP.put("借款", "中华人民共和国民法");
        LAW_TYPE_MAP.put("借钱", "中华人民共和国民法");
        LAW_TYPE_MAP.put("欠款", "中华人民共和国民法");
        LAW_TYPE_MAP.put("欠薪", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("拖欠", "中华人民共和国劳动法");
        LAW_TYPE_MAP.put("还钱", "中华人民共和国民法");
        LAW_TYPE_MAP.put("高利贷", "中华人民共和国民法");
        LAW_TYPE_MAP.put("担保", "中华人民共和国民法");
        LAW_TYPE_MAP.put("抵押", "中华人民共和国民法");
        LAW_TYPE_MAP.put("买卖", "中华人民共和国民法");
        LAW_TYPE_MAP.put("租赁", "中华人民共和国民法");
        // 房产 → 中华人民共和国民法
        LAW_TYPE_MAP.put("房产", "中华人民共和国民法");
        LAW_TYPE_MAP.put("买房", "中华人民共和国民法");
        LAW_TYPE_MAP.put("租房", "中华人民共和国民法");
        LAW_TYPE_MAP.put("房租", "中华人民共和国民法");
        LAW_TYPE_MAP.put("产权", "中华人民共和国民法");
        LAW_TYPE_MAP.put("物业", "中华人民共和国民法");
        LAW_TYPE_MAP.put("拆迁", "中华人民共和国民法");
        // 刑法相关 → 中华人民共和国刑法
        LAW_TYPE_MAP.put("刑法", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("犯罪", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("刑事", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("判刑", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("盗窃", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("诈骗", "中华人民共和国反电信网络诈骗法");
        LAW_TYPE_MAP.put("电信诈骗", "中华人民共和国反电信网络诈骗法");
        LAW_TYPE_MAP.put("网络诈骗", "中华人民共和国反电信网络诈骗法");
        LAW_TYPE_MAP.put("故意伤害", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("正当防卫", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("自首", "中华人民共和国刑法");
        LAW_TYPE_MAP.put("缓刑", "中华人民共和国刑法");
        // 交通相关 → 中华人民共和国道路交通安全法
        LAW_TYPE_MAP.put("交通", "中华人民共和国道路交通安全法");
        LAW_TYPE_MAP.put("车祸", "中华人民共和国道路交通安全法");
        LAW_TYPE_MAP.put("交通事故", "中华人民共和国道路交通安全法");
        LAW_TYPE_MAP.put("开车", "中华人民共和国道路交通安全法");
        LAW_TYPE_MAP.put("酒驾", "中华人民共和国道路交通安全法");
        LAW_TYPE_MAP.put("醉驾", "中华人民共和国道路交通安全法");
        LAW_TYPE_MAP.put("肇事", "中华人民共和国道路交通安全法");
        // 消费者权益 → 中华人民共和国消费者权益保护法
        LAW_TYPE_MAP.put("消费者", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("买东西", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("假货", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("退货", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("退款", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("食品安全", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("退一赔三", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("网购", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("商家", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("欺诈", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("商品", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("购买", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("下单", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("维权", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("消费纠纷", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("虚假宣传", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("三包", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("消费欺诈", "中华人民共和国消费者权益保护法");
        LAW_TYPE_MAP.put("商家欺诈", "中华人民共和国消费者权益保护法");
        // 公司法
        LAW_TYPE_MAP.put("公司", "中华人民共和国公司法");
        LAW_TYPE_MAP.put("股东", "中华人民共和国公司法");
        LAW_TYPE_MAP.put("股权", "中华人民共和国公司法");
        LAW_TYPE_MAP.put("注册公司", "中华人民共和国公司法");
        LAW_TYPE_MAP.put("法人", "中华人民共和国公司法");
        LAW_TYPE_MAP.put("破产", "中华人民共和国公司法");
        // 个人信息保护
        LAW_TYPE_MAP.put("个人信息", "中华人民共和国个人信息保护法");
        LAW_TYPE_MAP.put("隐私", "中华人民共和国个人信息保护法");
        LAW_TYPE_MAP.put("信息泄露", "中华人民共和国个人信息保护法");
        LAW_TYPE_MAP.put("个人数据", "中华人民共和国个人信息保护法");
        // 网络安全
        LAW_TYPE_MAP.put("网络安全", "中华人民共和国网络安全法");
        LAW_TYPE_MAP.put("网络攻击", "中华人民共和国网络安全法");
        LAW_TYPE_MAP.put("黑客", "中华人民共和国网络安全法");
        // 其他
        LAW_TYPE_MAP.put("宪法", "中华人民共和国宪法");
        LAW_TYPE_MAP.put("治安", "中华人民共和国治安管理处罚法");
        LAW_TYPE_MAP.put("打架", "中华人民共和国治安管理处罚法");
        LAW_TYPE_MAP.put("行政处罚", "中华人民共和国治安管理处罚法");
        LAW_TYPE_MAP.put("侵权", "中华人民共和国民法");
        LAW_TYPE_MAP.put("赔偿", "中华人民共和国民法");
        LAW_TYPE_MAP.put("商标", "中华人民共和国民法");
        LAW_TYPE_MAP.put("专利", "中华人民共和国民法");
        LAW_TYPE_MAP.put("著作权", "中华人民共和国民法");
        LAW_TYPE_MAP.put("版权", "中华人民共和国民法");
        LAW_TYPE_MAP.put("知识产权", "中华人民共和国民法");
    }

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[万万千千百十]?\\s*元");
    private static final Pattern MONTHLY_SALARY_PATTERN =
            Pattern.compile("(?:月工资|月薪|工资)(\\d+(?:\\.\\d+)?)\\s*[万万千千百十]?\\s*元?");
    private static final Pattern TIME_PERIOD_PATTERN =
            Pattern.compile("(\\d+)\\s*[个]?\\s*([年天日周月个])(?!\\d)");
    private static final Pattern ARTICLE_REF_PATTERN =
            Pattern.compile("(?:《([^》]+)》)?\\s*第([一二三四五六七八九十百零〇\\d]+)[条节章编款]");

    /**
     * 抽取的法律实体
     * lawType: 法律类型（如劳动、婚姻、合同等）
     * articleReference: 法条引用（如 "第39条"、"民法典第1046条"）
     * amountText: 金额文本（如 "5000元"、"月薪8000元"）
     * partyType: 当事人类型（如用人单位、劳动者、消费者、公司等）
     * timeRef: 时间信息（如 "3年"、"6个月"、"30天"）
     */
    public static class LegalEntities {
        private String lawType;
        private String articleReference;
        private String amountText;
        private String partyType;
        private String timeRef;
        private final List<String> allTypes = new ArrayList<>();

        public String getLawType() { return lawType; }
        public void setLawType(String lawType) { this.lawType = lawType; }
        public String getArticleReference() { return articleReference; }
        public void setArticleReference(String articleReference) { this.articleReference = articleReference; }
        public String getAmountText() { return amountText; }
        public void setAmountText(String amountText) { this.amountText = amountText; }
        public String getPartyType() { return partyType; }
        public void setPartyType(String partyType) { this.partyType = partyType; }
        public String getTimeRef() { return timeRef; }
        public void setTimeRef(String timeRef) { this.timeRef = timeRef; }
        public List<String> getAllTypes() { return allTypes; }
        public void addType(String type) { if (!allTypes.contains(type)) allTypes.add(type); }
    }

    /**
     * 从用户问题中抽取法律实体
     * @param question 用户输入的问题文本
     * @return LegalEntities 包含抽取的法律实体信息
     */
    public LegalEntities extract(String question) {
        LegalEntities entities = new LegalEntities();

        if (question == null || question.isBlank()) {
            return entities;
        }

        // 1. 抽取法律类型
        for (Map.Entry<String, String> entry : LAW_TYPE_MAP.entrySet()) {
            if (question.contains(entry.getKey())) {
                if (entities.lawType == null) {
                    // 优先设置 lawType 字段为第一个匹配的法律类型，作为主要过滤条件
                    entities.setLawType(entry.getValue());
                }
                entities.addType(entry.getValue());
            }
        }

        // 2. 抽取法条引用
        Matcher articleMatcher = ARTICLE_REF_PATTERN.matcher(question);
        if (articleMatcher.find()) {
            String lawName = articleMatcher.group(1);
            String articleNum = articleMatcher.group(2);
            if (lawName != null) {
                entities.setArticleReference(lawName + " 第" + articleNum + "条");
            } else {
                entities.setArticleReference("第" + articleNum + "条");
            }
        }

        // 3. 抽取金额
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(question);
        if (amountMatcher.find()) {
            entities.setAmountText(amountMatcher.group());
        } else {
            Matcher salaryMatcher = MONTHLY_SALARY_PATTERN.matcher(question);
            if (salaryMatcher.find()) {
                entities.setAmountText(salaryMatcher.group());
            }
        }

        // 4. 抽取当事人类型
        entities.setPartyType(extractPartyType(question));

        // 5. 抽取时间信息
        Matcher timeMatcher = TIME_PERIOD_PATTERN.matcher(question);
        if (timeMatcher.find()) {
            entities.setTimeRef(timeMatcher.group());
        }

        log.info("实体抽取完成: lawType={}, articleRef={}, amount={}, partyType={}, timeRef={}, allTypes={}",
                entities.getLawType(), entities.getArticleReference(), entities.getAmountText(),
                entities.getPartyType(), entities.getTimeRef(), entities.getAllTypes());

        return entities;
    }

    // 当事人类型抽取规则：根据关键词判断是用人单位、劳动者、消费者还是经营者
    private static final String[][] PARTY_TYPE_RULES = {
            {"用人单位", "公司", "企业", "单位", "老板", "雇主", "厂里", "店里"}, // 用人单位方
            {"劳动者", "员工", "工人", "职工", "打工", "上班", "工作"},           // 劳动者方
            {"消费者", "买家", "顾客", "买到了", "买到"},                         // 消费者方
            {"卖家", "商家", "店家", "店铺", "网店"}                              // 经营者方
    };

    // 根据关键词抽取当事人类型
    private String extractPartyType(String question) {
        for (int i = 0; i < PARTY_TYPE_RULES.length; i++) {
            for (String keyword : PARTY_TYPE_RULES[i]) {
                if (question.contains(keyword)) {
                    return switch (i) {
                        case 0 -> "用人单位";
                        case 1 -> "劳动者";
                        case 2 -> "消费者";
                        case 3 -> "经营者";
                        default -> null;
                    };
                }
            }
        }
        return null;
    }
}
