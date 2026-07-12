package com.lhs.lawmind.utils;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律文档元数据提取器
 * 从法律文本中提取 lawType、publisher、publishDate
 * 优先使用正则匹配，失败时借助 AI 辅助判断
 */
@Slf4j
@Component
public class LegalMetadataExtractor {

    private final ChatLanguageModel chatLanguageModel;

    // 匹配格式：中华人民共和国XXX法/条例/决定/办法/细则/规定/意见/规则/规程/规范/通则/解释/指引/守则
    private static final Pattern LAW_TYPE_PATTERN =
            Pattern.compile("中华人民共和国([^法条例决定办法则规定见程范通则释引守]+[法条例决定办法则规定见程范通则释引守])");

    // 匹配书名号中的法律名：《XXX法》《XXX条例》等
    private static final Pattern LAW_TYPE_BOOK_TITLE =
            Pattern.compile("《([^》]{1,40}(?:法|条例|决定|办法|细则|规定|意见|规则|规程|规范|通则|解释|指引|守则|方案|标准))》");

    // 匹配独立出现的常见法律简称：刑法、民法典、宪法、劳动法、合同法等
    private static final Pattern LAW_TYPE_STANDALONE =
            Pattern.compile("(?:^|[\\s。，；])(宪法|刑法|民法典|民法通则|劳动法|合同法|物权法|侵权责任法|公司法|证券法|保险法|"
                    + "行政诉讼法|民事诉讼法|刑事诉讼法|行政诉讼法|行政处罚法|行政许可法|行政强制法|"
                    + "行政复议法|国家赔偿法|立法法|监督法|选举法|"
                    + "环境保护法|食品安全法|药品管理法|道路交通安全法|"
                    + "专利法|商标法|著作权法|反垄断法|反不正当竞争法|"
                    + "企业破产法|票据法|海商法|民用航空法|"
                    + "突发事件应对法|网络安全法|数据安全法|个人信息保护法)(?:[\\s。，；]|$)");

    private static final Pattern PUBLISH_DATE_PATTERN =
            Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");

    // 法律文档头部括号内的通过/修正信息，如：
    // （2016年11月7日第十二届全国人民代表大会常务委员会第二十四次会议通过）
    private static final Pattern HEADER_PARENS_PATTERN =
            Pattern.compile("[（(]\\s*\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日(.+?)[）)]");

    // 从头部信息中提取发布机构：全国XXX大会、全国XXX委员会、国务院等
    private static final Pattern ORG_IN_HEADER_PATTERN =
            Pattern.compile("(全国[^，。；、]{1,40}(?:大会|委员会)|国务院|最高人民法院|最高人民检察院|中央军事委员会)");

    // 前 1000 字内的已知机构名（即便没有"通过""发布"等动词，单独出现也高概率是发布者）
    private static final Pattern KNOWN_PUBLISHER_PATTERN =
            Pattern.compile("(全国人民代表大会(?:常务委员会)?|国务院|最高人民法院|最高人民检察院|中央军事委员会)");

    // 机构名 + 发布动词
    private static final Pattern PUBLISHER_ACTION_PATTERN =
            Pattern.compile("([一-龥]{2,20}(?:部|院|局|委|会|署|行|厅))\\s*(?:发布|令|公告|通知)");

    // 经/由 + 机构名
    private static final Pattern JING_YOU_PATTERN =
            Pattern.compile("(?:经|由)\\s*([一-龥]{2,20}(?:部|院|局|委|会|署|行|厅))");

    // 通用机构名（最低优先级正则）
    private static final Pattern GENERIC_ORG_PATTERN =
            Pattern.compile("([一-龥]{2,20}(?:部|院|局|委|会|署|行|厅))");

    public LegalMetadataExtractor(Optional<ChatLanguageModel> chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel.orElse(null);
    }

    public MetadataResult extract(String text, String fileName) {
        String lawType = extractLawType(text, fileName);
        String publisher = extractPublisher(text);
        Date publishDate = extractPublishDate(text);
        String documentType = detectDocumentType(text, lawType);
        String effectivenessLevel = inferEffectivenessLevel(publisher);
        return new MetadataResult(lawType, publisher, publishDate, documentType, effectivenessLevel);
    }

    private String extractLawType(String text, String fileName) {
        // 优先级1：正文中匹配"中华人民共和国XXX法"
        Matcher matcher = LAW_TYPE_PATTERN.matcher(text);
        if (matcher.find()) {
            return "中华人民共和国" + matcher.group(1);
        }
        // 优先级2：书名号中匹配《XXX法》
        Matcher bookMatcher = LAW_TYPE_BOOK_TITLE.matcher(text);
        if (bookMatcher.find()) {
            return bookMatcher.group(1);
        }
        // 优先级3：独立常见法律简称
        Matcher standaloneMatcher = LAW_TYPE_STANDALONE.matcher(text);
        if (standaloneMatcher.find()) {
            return standaloneMatcher.group(1);
        }
        // 优先级4：从文件名匹配
        if (fileName != null) {
            Matcher fileMatcher = LAW_TYPE_PATTERN.matcher(fileName);
            if (fileMatcher.find()) {
                return "中华人民共和国" + fileMatcher.group(1);
            }
            Matcher fileBookMatcher = LAW_TYPE_BOOK_TITLE.matcher(fileName);
            if (fileBookMatcher.find()) {
                return fileBookMatcher.group(1);
            }
        }
        return null;
    }

    /**
     * 提取发布机构，按优先级依次尝试：
     * 1. 文档头部括号中的通过/发布机构（最可靠）
     * 2. 前 1000 字内的已知发布机构
     * 3. 机构名 + 发布/令/公告/通知 模式
     * 4. 经/由 + 机构名 模式
     * 5. AI 辅助提取
     */
    private String extractPublisher(String text) {
        // ── 优先级 1：文档头部括号信息 ──
        String fromHeader = extractFromHeaderParens(text);
        if (fromHeader != null) {
            log.info("发布机构（头部匹配）: {}", fromHeader);
            return fromHeader;
        }

        // ── 优先级 2：前 1000 字内的已知机构 ──
        String headerArea = text.length() > 1000 ? text.substring(0, 1000) : text;
        Matcher knownMatcher = KNOWN_PUBLISHER_PATTERN.matcher(headerArea);
        if (knownMatcher.find()) {
            String org = knownMatcher.group(1);
            log.info("发布机构（已知机构）: {}", org);
            return org;
        }

        // ── 优先级 3：机构名 + 发布动词 ──
        Matcher actionMatcher = PUBLISHER_ACTION_PATTERN.matcher(text);
        if (actionMatcher.find()) {
            String org = actionMatcher.group(1);
            log.info("发布机构（动作匹配）: {}", org);
            return org;
        }

        // ── 优先级 4：经/由 + 机构名 ──
        Matcher jingYouMatcher = JING_YOU_PATTERN.matcher(text);
        if (jingYouMatcher.find()) {
            String org = jingYouMatcher.group(1);
            log.info("发布机构（经由匹配）: {}", org);
            return org;
        }

        // ── 优先级 5：AI 辅助提取 ──
        String fromAi = extractPublisherWithAI(text);
        if (fromAi != null) {
            log.info("发布机构（AI 提取）: {}", fromAi);
            return fromAi;
        }

        // ── 优先级 6：通用正则兜底 ──
        Matcher genericMatcher = GENERIC_ORG_PATTERN.matcher(text);
        if (genericMatcher.find()) {
            String org = genericMatcher.group(1);
            log.info("发布机构（通用匹配）: {}", org);
            return org;
        }

        log.warn("未能提取发布机构");
        return null;
    }

    /**
     * 从文档头部括号信息中提取发布机构
     * 例如：（2016年11月7日第十二届全国人民代表大会常务委员会第二十四次会议通过）
     * → 全国人民代表大会常务委员会
     */
    private String extractFromHeaderParens(String text) {
        String header = text.length() > 2000 ? text.substring(0, 2000) : text;
        Matcher parensMatcher = HEADER_PARENS_PATTERN.matcher(header);
        while (parensMatcher.find()) {
            String parensContent = parensMatcher.group(1);
            Matcher orgMatcher = ORG_IN_HEADER_PATTERN.matcher(parensContent);
            if (orgMatcher.find()) {
                return orgMatcher.group(1);
            }
        }
        return null;
    }

    /**
     * 使用 AI 从文档头部提取发布机构
     * 仅发送前 1500 字，减少 token 消耗
     */
    private String extractPublisherWithAI(String text) {
        if (chatLanguageModel == null) return null;
        try {
            String header = text.length() > 1500 ? text.substring(0, 1500) : text;
            String prompt = """
                    你是一个中国法律文档分析助手。请从以下法律文档的头部信息中提取"发布机构"。

                    发布机构是指通过或发布该法律的最高权力机关，例如：
                    - 全国人民代表大会
                    - 全国人民代表大会常务委员会
                    - 国务院
                    - 最高人民法院
                    - 最高人民检察院
                    - 中央军事委员会

                    文档头部内容：
                    """
                    + header + "\n\n"
                    + "请只输出发布机构的完整名称，不要输出其他内容。如果无法判断，请输出 UNKNOWN。";

            String result = chatLanguageModel.generate(prompt);
            if (result != null) {
                result = result.trim();
                if (!result.isEmpty() && !"UNKNOWN".equalsIgnoreCase(result)) {
                    // 清理 AI 输出中可能的引号、句号等
                    result = result.replaceAll("[\"'。，\\s]", "");
                    if (result.length() >= 2 && result.length() <= 50) {
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI 提取发布机构失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 提取发布日期，匹配格式如 "2016年11月7日"
     * @param text
     * @return
     */
    /**
     * 检测文档类型：法律条文 / 合同文本 / 裁判文书 / 通用文本
     */
    private String detectDocumentType(String text, String lawType) {
        if (text == null) return "通用文本";

        // 法律条文特征：有"中华人民共和国XXX法"或有章/节/条结构
        if (lawType != null || text.contains("中华人民共和国")
                || text.contains("第") && (text.contains("章") || text.contains("条"))) {
            return "法律条文";
        }

        // 合同文本特征：出现"甲方""乙方""合同""协议"
        if (text.contains("甲方") || text.contains("乙方")
                || text.contains("合同编号") || text.contains("签订日期")) {
            return "合同文本";
        }

        // 裁判文书特征：出现"本院认为""判决如下""裁定如下"
        if (text.contains("本院认为") || text.contains("判决如下")
                || text.contains("裁定如下") || text.contains("原告")
                || text.contains("被告")) {
            return "裁判文书";
        }

        return "通用文本";
    }

    /**
     * 根据发布机构推断效力级别
     */
    private String inferEffectivenessLevel(String publisher) {
        if (publisher == null) return null;
        if (publisher.contains("全国人民代表大会") && !publisher.contains("常务委员会")) return "基本法律";
        if (publisher.contains("全国人民代表大会常务委员会")) return "法律";
        if (publisher.contains("国务院")) return "行政法规";
        if (publisher.contains("最高人民法院") || publisher.contains("最高人民检察院")) return "司法解释";
        if (publisher.contains("省") && (publisher.contains("人民代表大会") || publisher.contains("人民政府"))) return "地方性法规";
        if (publisher.endsWith("部") || publisher.endsWith("委") || publisher.endsWith("局")) return "部门规章";
        return "规范性文件";
    }

    private Date extractPublishDate(String text) {
        Matcher matcher = PUBLISH_DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return java.sql.Date.valueOf(
                    String.format("%04d-%02d-%02d", year, month, day));
        }
        return null;
    }

    public record MetadataResult(String lawType, String publisher, Date publishDate,
                                  String documentType, String effectivenessLevel) {
    }
}
