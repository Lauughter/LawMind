package com.lhs.lawmind.skill;

import java.util.List;
import java.util.Map;

/**
 * 一个完整加载的 Skill 配置，包含所有从 skill 目录中解析出的资源。
 *
 * @param manifest          Skill 元数据
 * @param systemPrompt      prompt/system-message.md 原文
 * @param userPromptTemplate prompt/user-prompt-template.md 原文（含 {placeholder}）
 * @param checklists        审查清单项
 * @param patterns          不公平条款检测模式
 */
public record SkillConfig(
        SkillManifest manifest,
        String systemPrompt,
        String userPromptTemplate,
        List<ChecklistItem> checklists,
        List<UnfairClausePattern> patterns
) {

    public record ChecklistItem(
            String id,
            String dimension,
            String checkPoint,
            String severity,
            String legalRef,
            String knowledgeSearch
    ) {}

    public record UnfairClausePattern(
            String id,
            List<String> keywords,
            String legalBasis,
            String knowledgeSearch,
            String severity,
            String suggestion
    ) {}

    /**
     * 组装完整的 System Prompt：基础 system-message + 不公平条款模式列表。
     */
    public String assembleSystemPrompt() {
        StringBuilder sb = new StringBuilder(systemPrompt);

        if (!patterns.isEmpty()) {
            sb.append("\n\n## 常见不公平条款检测模式\n\n");
            for (var p : patterns) {
                sb.append("- **").append(p.id()).append("**");
                if (p.keywords() != null && !p.keywords().isEmpty()) {
                    sb.append(" — 关键词: `").append(String.join("`, `", p.keywords())).append("`");
                }
                sb.append("\n  法条依据: ").append(p.legalBasis())
                        .append("\n  建议: ").append(p.suggestion()).append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * 将用户提示词模板中的占位符替换为实际值。
     *
     * @param contractType 合同类型（中文描述）
     * @param contractText 合同全文
     * @return 填充后的审查指令
     */
    public String fillUserPrompt(String contractType, String contractText) {
        return userPromptTemplate
                .replace("{contract_type}", contractType != null ? contractType : "未知类型")
                .replace("{contract_text}", contractText != null ? contractText : "");
    }

    /**
     * 批量替换模板占位符。
     */
    public String fillUserPrompt(Map<String, String> variables) {
        String result = userPromptTemplate;
        for (var entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
