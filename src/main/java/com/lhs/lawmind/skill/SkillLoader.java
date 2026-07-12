package com.lhs.lawmind.skill;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从文件系统加载单个 Skill 目录。
 *
 * <p>负责读取 manifest.yaml、prompt/*.md、checklists/*.yaml、patterns/*.yaml
 * 并组装为 {@link SkillConfig}。</p>
 */
@Slf4j
public class SkillLoader {

    private final Path skillDir;
    private final Yaml yaml;

    public SkillLoader(Path skillDir) {
        this.skillDir = skillDir;
        this.yaml = new Yaml();
    }

    public String skillName() {
        return skillDir.getFileName().toString();
    }

    public Path skillDir() {
        return skillDir;
    }

    /**
     * 加载完整的 Skill 配置。
     */
    @SuppressWarnings("unchecked")
    public SkillConfig load() throws IOException {
        SkillManifest manifest = loadManifest();
        String systemPrompt = loadTextFile("prompt", "system-message.md");
        String userPromptTemplate = loadTextFile("prompt", "user-prompt-template.md");
        List<SkillConfig.ChecklistItem> checklists = loadChecklists();
        List<SkillConfig.UnfairClausePattern> patterns = loadPatterns();

        log.info("[SkillLoader] 加载 Skill 完成: name={}, version={}, checklists={}, patterns={}",
                manifest.name(), manifest.version(), checklists.size(), patterns.size());

        return new SkillConfig(manifest, systemPrompt, userPromptTemplate, checklists, patterns);
    }

    @SuppressWarnings("unchecked")
    private SkillManifest loadManifest() throws IOException {
        Path file = skillDir.resolve("manifest.yaml");
        if (!Files.exists(file)) {
            throw new IOException("Skill manifest 文件不存在: " + file);
        }

        Map<String, Object> data = yaml.load(Files.newInputStream(file));
        if (data == null) {
            throw new IOException("Skill manifest 文件为空: " + file);
        }

        String name = strVal(data, "name", skillDir.getFileName().toString());
        String version = strVal(data, "version", "0.0.0");
        String description = strVal(data, "description", "");
        String triggerIntent = strVal(data, "trigger_intent", "");
        boolean knowledgeSearchRequired = boolVal(data, "knowledge_search_required", false);
        String model = strVal(data, "model", "");
        int maxIterations = intVal(data, "max_iterations", 10);

        List<String> requiredTools = Collections.emptyList();
        Object toolsRaw = data.get("required_tools");
        if (toolsRaw instanceof List<?> list) {
            requiredTools = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        return new SkillManifest(name, version, description, triggerIntent,
                requiredTools, knowledgeSearchRequired, model, maxIterations);
    }

    @SuppressWarnings("unchecked")
    private List<SkillConfig.ChecklistItem> loadChecklists() {
        Path checklistDir = skillDir.resolve("checklists");
        if (!Files.isDirectory(checklistDir)) {
            return Collections.emptyList();
        }

        List<SkillConfig.ChecklistItem> all = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(checklistDir, "*.yaml")) {
            for (Path file : stream) {
                Map<String, Object> data = yaml.load(Files.newInputStream(file));
                if (data == null) continue;

                Object checklistRaw = data.get("checklist");
                if (checklistRaw instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            all.add(parseChecklistItem((Map<String, Object>) m));
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[SkillLoader] 加载审查清单失败: skill={}, error={}",
                    skillName(), e.getMessage());
        }
        return all;
    }

    private SkillConfig.ChecklistItem parseChecklistItem(Map<String, Object> m) {
        return new SkillConfig.ChecklistItem(
                strVal(m, "id", ""),
                strVal(m, "dimension", ""),
                strVal(m, "check_point", ""),
                strVal(m, "severity", "MEDIUM"),
                strVal(m, "legal_ref", ""),
                strVal(m, "knowledge_search", "")
        );
    }

    @SuppressWarnings("unchecked")
    private List<SkillConfig.UnfairClausePattern> loadPatterns() {
        Path patternsDir = skillDir.resolve("patterns");
        if (!Files.isDirectory(patternsDir)) {
            return Collections.emptyList();
        }

        List<SkillConfig.UnfairClausePattern> all = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(patternsDir, "*.yaml")) {
            for (Path file : stream) {
                Map<String, Object> data = yaml.load(Files.newInputStream(file));
                if (data == null) continue;

                Object patternsRaw = data.get("patterns");
                if (patternsRaw instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            all.add(parsePattern((Map<String, Object>) m));
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[SkillLoader] 加载不公平条款模式失败: skill={}, error={}",
                    skillName(), e.getMessage());
        }
        return all;
    }

    private SkillConfig.UnfairClausePattern parsePattern(Map<String, Object> m) {
        List<String> keywords = Collections.emptyList();
        Object kwRaw = m.get("keywords");
        if (kwRaw instanceof List<?> list) {
            keywords = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        return new SkillConfig.UnfairClausePattern(
                strVal(m, "id", ""),
                keywords,
                strVal(m, "legal_basis", ""),
                strVal(m, "knowledge_search", ""),
                strVal(m, "severity", "MEDIUM"),
                strVal(m, "suggestion", "")
        );
    }

    private String loadTextFile(String... pathParts) throws IOException {
        Path file = skillDir.resolve(Path.of("", pathParts));
        if (!Files.exists(file)) {
            throw new IOException("Skill 文件不存在: " + file);
        }
        return Files.readString(file, StandardCharsets.UTF_8).trim();
    }

    // ---- YAML value helpers ----

    private static String strVal(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private static boolean boolVal(Map<String, Object> map, String key, boolean defaultValue) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return defaultValue;
    }

    private static int intVal(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
