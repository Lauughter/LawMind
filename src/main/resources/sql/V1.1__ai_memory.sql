-- LawMind 记忆系统 — 统一记忆表
-- 四类型记忆模型：USER / FEEDBACK / PROJECT / REFERENCE
-- 执行方式：mysql -u root -p lawmind < V1.1__ai_memory.sql

CREATE TABLE IF NOT EXISTS ai_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户 ID',

    -- 四类型区分
    type ENUM('USER', 'FEEDBACK', 'PROJECT', 'REFERENCE') NOT NULL COMMENT '记忆类型',

    -- 记忆内容
    title VARCHAR(100) COMMENT '简短标题（用于列表展示和一级检索）',
    body TEXT NOT NULL COMMENT '记忆正文（一句话事实或简短摘要）',
    summary VARCHAR(300) COMMENT '摘要（从 body 提炼，用于注入时节省 token）',

    -- 溯源
    origin_session_id BIGINT COMMENT '产生此记忆的会话 ID',
    source_session_ids JSON COMMENT '支撑此记忆的所有会话 ID',

    -- 质量评分
    confidence DOUBLE DEFAULT 0.5 COMMENT '置信度 0-1（多证据来源则升高）',
    importance DOUBLE DEFAULT 0.5 COMMENT '重要性 0-10 归一化到 0-1',
    access_count INT DEFAULT 0 COMMENT '被检索命中次数',

    -- 向量
    embedding JSON COMMENT '1536 维向量（JSON 数组）',

    -- 生命周期
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_accessed_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_type (user_id, type),
    INDEX idx_user_importance (user_id, importance DESC),
    INDEX idx_user_decay (user_id, last_accessed_at, type),
    INDEX idx_origin_session (origin_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一记忆表——LawMind 跨会话记忆的唯一存储';
