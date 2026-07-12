package com.lhs.lawmind.skill;

import java.util.List;

/**
 * {@code manifest.yaml} 的 Java 映射。
 *
 * <p>每个 skill 目录下的 manifest.yaml 描述该 skill 的元数据、触发条件、
 * 所需工具和默认模型配置。SkillLoader 在启动时解析此文件。</p>
 */
public record SkillManifest(
        String name,
        String version,
        String description,
        String triggerIntent,
        List<String> requiredTools,
        boolean knowledgeSearchRequired,
        String model,
        int maxIterations
) {}
