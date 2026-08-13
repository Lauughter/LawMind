package com.lhs.lawmind.rag;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.LawKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 回答中法条引用验证器 —— 从回答提取引用并与检索知识库逐条核对。
 * 从 RagServiceImpl 拆出。
 */
@Slf4j
@Component
public class CitationVerifier {

    private final RagConfig ragConfig;

    public CitationVerifier(RagConfig ragConfig) {
        this.ragConfig = ragConfig;
    }

    /**
     * 验证回答中的法律引用是否在检索到的知识库结果中有依据。
     *
     * @param answer          AI 生成的回答
     * @param relatedKnowledge 检索到的知识列表
     * @return true=所有引用均可验证, false=存在未验证引用
     */
    public boolean verifyCitations(String answer, List<LawKnowledge> relatedKnowledge) {
        if (!ragConfig.isCitationVerificationEnabled() || relatedKnowledge == null || relatedKnowledge.isEmpty()) {
            return true;
        }
        java.util.List<String> citations = extractCitations(answer);
        if (citations.isEmpty()) {
            return true;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:《([^》]{1,30})》)?\\s*第([一二三四五六七八九十百千万零〇\\d]+)条");
        boolean allVerified = true;
        for (String citation : citations) {
            java.util.regex.Matcher m = p.matcher(citation);
            if (!m.find()) continue;
            String lawName = m.group(1);
            String articleNumStr = m.group(2);
            int articleNum = chineseNumToArabic(articleNumStr);
            boolean verified = false;
            for (LawKnowledge k : relatedKnowledge) {
                boolean lawMatch = lawName == null
                        || (k.getTitle() != null && k.getTitle().contains(lawName))
                        || (k.getLawType() != null && k.getLawType().contains(lawName));
                boolean articleMatch = articleNum < 0
                        || (k.getArticleNumber() != null && k.getArticleNumber() == articleNum)
                        || (k.getTitle() != null && k.getTitle().contains("第" + articleNumStr + "条"))
                        || (k.getContent() != null && k.getContent().contains("第" + articleNumStr + "条"));
                if (lawMatch && articleMatch) {
                    verified = true;
                    break;
                }
            }
            if (!verified) {
                log.warn("[RAG] 引用未验证: citation=\"{}\" articleNum={}", citation.trim(), articleNum);
                allVerified = false;
            }
        }
        if (!allVerified) {
            log.warn("[RAG] 回答中存在{}条未验证的法律引用（共{}条引用）",
                    citations.stream().filter(c -> {
                        java.util.regex.Matcher cm = p.matcher(c);
                        if (!cm.find()) return false;
                        String lawNameCheck = cm.group(1);
                        String numCheck = cm.group(2);
                        int an = chineseNumToArabic(numCheck);
                        return relatedKnowledge.stream().noneMatch(k -> {
                            boolean lm = lawNameCheck == null
                                    || (k.getTitle() != null && k.getTitle().contains(lawNameCheck))
                                    || (k.getLawType() != null && k.getLawType().contains(lawNameCheck));
                            boolean am = an < 0
                                    || (k.getArticleNumber() != null && k.getArticleNumber() == an)
                                    || (k.getTitle() != null && k.getTitle().contains("第" + numCheck + "条"))
                                    || (k.getContent() != null && k.getContent().contains("第" + numCheck + "条"));
                            return lm && am;
                        });
                    }).count(), citations.size());
        }
        return allVerified;
    }

    /**
     * 从回答文本中提取法律引用列表（《法律名称》第X条 或 第X条）。
     */
    private java.util.List<String> extractCitations(String answer) {
        java.util.List<String> citations = new java.util.ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:《([^》]{1,30})》)?\\s*第([一二三四五六七八九十百千万零〇\\d]+)条");
        java.util.regex.Matcher m = p.matcher(answer);
        while (m.find()) {
            citations.add(m.group());
        }
        return citations;
    }

    private static final java.util.Map<Character, Integer> CN_NUM_MAP = java.util.Map.ofEntries(
            java.util.Map.entry('零', 0), java.util.Map.entry('〇', 0),
            java.util.Map.entry('一', 1), java.util.Map.entry('二', 2),
            java.util.Map.entry('三', 3), java.util.Map.entry('四', 4),
            java.util.Map.entry('五', 5), java.util.Map.entry('六', 6),
            java.util.Map.entry('七', 7), java.util.Map.entry('八', 8),
            java.util.Map.entry('九', 9), java.util.Map.entry('十', 10),
            java.util.Map.entry('百', 100), java.util.Map.entry('千', 1000),
            java.util.Map.entry('万', 10000)
    );

    /**
     * 将中文数字（如"第八十七条"、"第一千一百七十九条"）转换为阿拉伯数字。
     */
    private int chineseNumToArabic(String cnNum) {
        if (cnNum == null || cnNum.isEmpty()) return -1;
        try { return Integer.parseInt(cnNum); } catch (NumberFormatException ignored) {}
        int result = 0;
        int temp = 0;
        int lastUnit = 1;
        for (int i = 0; i < cnNum.length(); i++) {
            char c = cnNum.charAt(i);
            Integer val = CN_NUM_MAP.get(c);
            if (val == null) return -1;
            if (val >= 10) {
                if (temp == 0) temp = 1;
                if (val == 10000) {
                    result = (result + temp) * val;
                    temp = 0;
                } else {
                    temp *= val;
                    if (val > lastUnit) lastUnit = val;
                }
            } else {
                temp = temp * 10 + val;
            }
        }
        result += temp;
        return result;
    }
}
