package com.lhs.lawmind.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动创建记忆向量索引。
 */
@Slf4j
@Component
public class MemoryIndexInitializer implements CommandLineRunner {

    private final MemoryStore memoryStore;

    public MemoryIndexInitializer(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public void run(String... args) {
        try {
            memoryStore.ensureIndex();
            log.info("记忆向量索引入口初始化完成");
        } catch (Exception e) {
            log.error("记忆向量索引初始化失败: {}", e.getMessage(), e);
        }
    }
}
