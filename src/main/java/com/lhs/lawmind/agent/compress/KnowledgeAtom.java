package com.lhs.lawmind.agent.compress;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识原子 —— 从工具返回结果中提取的最小知识单元。
 * 使用 Java 17 sealed interface + record 实现不可变设计。
 */
public sealed interface KnowledgeAtom {

    String source();

    /** 法条知识原子 */
    record ArticleAtom(
            String lawName,
            String articleNumber,
            String keyRule,
            int citeCount,
            List<String> sources,
            boolean verified
    ) implements KnowledgeAtom {
        @Override
        public String source() {
            return String.join(" + ", sources);
        }

        public ArticleAtom mergeSource(ArticleAtom other) {
            List<String> merged = new ArrayList<>(this.sources);
            for (String s : other.sources) {
                if (!merged.contains(s)) {
                    merged.add(s);
                }
            }
            return new ArticleAtom(
                    this.lawName, this.articleNumber, this.keyRule,
                    this.citeCount + 1, List.copyOf(merged),
                    this.verified || other.verified
            );
        }

        public String toCompactString() {
            return String.format("《%s》第%s条 | %s | 引用%d次 | %s",
                    lawName, articleNumber, keyRule, citeCount,
                    verified ? "已核实" : "待核实");
        }
    }

    /** 金额计算知识原子 */
    record CalcAtom(
            String description,
            String formula,
            String source
    ) implements KnowledgeAtom {
        public String toCompactString() {
            return String.format("%s | 公式: %s | 来源: %s", description, formula, source);
        }
    }

    /** 参考案例知识原子 */
    record CaseAtom(
            String title,
            String keyPoint,
            String source
    ) implements KnowledgeAtom {
        public String toCompactString() {
            return String.format("%s | 要点: %s | 来源: %s", title, keyPoint, source);
        }
    }
}
