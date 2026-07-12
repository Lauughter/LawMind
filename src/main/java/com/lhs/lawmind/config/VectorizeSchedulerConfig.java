package com.lhs.lawmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量化定时任务配置类
 * 用于统一管理定时任务的配置参数
 */
@Component
@ConfigurationProperties(prefix = "lawmind.vectorize.scheduler")
public class VectorizeSchedulerConfig {

    /** 是否开启定时任务 */
    private boolean enabled = true;

    /** 上次执行完成后等待多久再次执行（毫秒），默认 60s */
    private long fixedDelay = 60000;

    /** 初始化任务延迟时间（毫秒），默认 5s */
    private long initialDelay = 5000;

    /** 每批从 DB 拉取的数据条数 */
    private int batchSize = 100;

    /** Embedding API 单次调用最大条数（DashScope 上限 25） */
    private int embedBatchSize = 25;

    /** 调度级分布式锁 Key */
    private String schedulerLockKey = "law:vectorize:scheduler:lock";

    /** 调度级锁过期时间（秒），防止死锁 */
    private long schedulerLockExpire = 120;

    // Getters and Setters

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(long fixedDelay) { this.fixedDelay = fixedDelay; }

    public long getInitialDelay() { return initialDelay; }
    public void setInitialDelay(long initialDelay) { this.initialDelay = initialDelay; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getEmbedBatchSize() { return embedBatchSize; }
    public void setEmbedBatchSize(int embedBatchSize) { this.embedBatchSize = embedBatchSize; }

    public String getSchedulerLockKey() { return schedulerLockKey; }
    public void setSchedulerLockKey(String schedulerLockKey) { this.schedulerLockKey = schedulerLockKey; }

    public long getSchedulerLockExpire() { return schedulerLockExpire; }
    public void setSchedulerLockExpire(long schedulerLockExpire) { this.schedulerLockExpire = schedulerLockExpire; }

    @Override
    public String toString() {
        return "VectorizeSchedulerConfig{" +
                "enabled=" + enabled +
                ", fixedDelay=" + fixedDelay +
                ", initialDelay=" + initialDelay +
                ", batchSize=" + batchSize +
                ", embedBatchSize=" + embedBatchSize +
                ", schedulerLockKey='" + schedulerLockKey + '\'' +
                ", schedulerLockExpire=" + schedulerLockExpire +
                '}';
    }
}
