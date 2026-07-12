package com.lhs.lawmind.skill;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill 生命周期管理器。
 *
 * <p>负责 Skill 的发现、加载、缓存和热更新。每个 {@code skills/} 子目录
 * 视为一个独立的 Skill 包。默认扫描项目根目录下的 {@code skills/} 目录。</p>
 *
 * <h3>热更新机制</h3>
 * <p>每次调用 {@link #getSkill(String)} 时检查该 skill 目录下所有源文件的最后修改时间。
 * 如有任何文件变更，自动重新加载。这意味着修改 skill 文件后无需重启服务，
 * 下一次请求即可使用更新后的配置。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // Controller 中注入 SkillManager
 * SkillConfig skill = skillManager.getSkill("contract-review");
 * String systemPrompt = skill.assembleSystemPrompt();
 * String userPrompt = skill.fillUserPrompt("劳动合同", contractText);
 * agentRunner.execute(userPrompt, systemPrompt);
 * }</pre>
 */
@Slf4j
@Component
public class SkillManager {

    private final Path skillsBaseDir;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SkillManager(
            @Value("${lawmind.skill.base-dir:skills}") String baseDir) {
        this.skillsBaseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        log.info("[SkillManager] 初始化: baseDir={}, exists={}",
                skillsBaseDir, Files.isDirectory(skillsBaseDir));
        if (Files.isDirectory(skillsBaseDir)) {
            discoverAndLoad();
        }
    }

    /**
     * 获取指定名称的 Skill 配置。
     * 自动检测文件变更并热加载。
     *
     * @param skillName skill 目录名（如 "contract-review"）
     * @return 加载后的 SkillConfig
     * @throws SkillNotFoundException 如果 skill 目录不存在
     */
    public SkillConfig getSkill(String skillName) {
        Path skillDir = skillsBaseDir.resolve(skillName);
        if (!Files.isDirectory(skillDir)) {
            throw new SkillNotFoundException(
                    "Skill 目录不存在: " + skillDir + "。可用 skills: " + listAvailableSkills());
        }

        CacheEntry entry = cache.get(skillName);
        if (entry != null && !hasAnyFileChanged(entry)) {
            return entry.config;
        }

        synchronized (this) {
            entry = cache.get(skillName);
            if (entry != null && !hasAnyFileChanged(entry)) {
                return entry.config;
            }
            return reload(skillName);
        }
    }

    /**
     * 强制重新加载指定 Skill。
     */
    public SkillConfig reload(String skillName) {
        Path skillDir = skillsBaseDir.resolve(skillName);
        if (!Files.isDirectory(skillDir)) {
            throw new SkillNotFoundException("Skill 目录不存在: " + skillDir);
        }

        SkillLoader loader = new SkillLoader(skillDir);
        try {
            SkillConfig config = loader.load();

            Map<Path, Long> timestamps = new ConcurrentHashMap<>();
            try (Stream<Path> files = Files.walk(skillDir)) {
                files.filter(Files::isRegularFile)
                        .forEach(f -> {
                            try {
                                timestamps.put(f, Files.getLastModifiedTime(f).toMillis());
                            } catch (IOException ignored) {
                            }
                        });
            }

            cache.put(skillName, new CacheEntry(config, timestamps));
            log.info("[SkillManager] Skill 加载/刷新成功: name={}, version={}, files={}",
                    skillName, config.manifest().version(), timestamps.size());
            return config;
        } catch (IOException e) {
            log.error("[SkillManager] Skill 加载失败: name={}, error={}", skillName, e.getMessage(), e);
            throw new SkillLoadException("加载 Skill 失败: " + skillName, e);
        }
    }

    /**
     * 列出所有可用 Skill 名称。
     */
    public Set<String> listAvailableSkills() {
        if (!Files.isDirectory(skillsBaseDir)) {
            return Collections.emptySet();
        }
        try (Stream<Path> dirs = Files.list(skillsBaseDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("manifest.yaml")))
                    .map(d -> d.getFileName().toString())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.warn("[SkillManager] 扫描 skill 目录失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private void discoverAndLoad() {
        Set<String> names = listAvailableSkills();
        log.info("[SkillManager] 发现 {} 个 Skill: {}", names.size(), names);
        for (String name : names) {
            try {
                reload(name);
            } catch (Exception e) {
                log.error("[SkillManager] Skill 加载失败: name={}, error={}", name, e.getMessage());
            }
        }
    }

    private boolean hasAnyFileChanged(CacheEntry entry) {
        for (var fileEntry : entry.fileTimestamps.entrySet()) {
            try {
                long current = Files.getLastModifiedTime(fileEntry.getKey()).toMillis();
                if (current != fileEntry.getValue()) {
                    log.info("[SkillManager] 检测到文件变更: {}", fileEntry.getKey());
                    return true;
                }
            } catch (IOException e) {
                return true;
            }
        }
        return false;
    }

    private record CacheEntry(SkillConfig config, Map<Path, Long> fileTimestamps) {}

    public static class SkillNotFoundException extends RuntimeException {
        public SkillNotFoundException(String message) {
            super(message);
        }
    }

    public static class SkillLoadException extends RuntimeException {
        public SkillLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
