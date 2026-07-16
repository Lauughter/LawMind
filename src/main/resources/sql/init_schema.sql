-- ============================================================
-- LawMind 数据库初始化脚本 (V1.0)
-- 包含用户、会话、知识库、向量任务、审计等全部核心表
-- 执行方式: mysql -u root -p <database_name> < V1.0__init_schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS lawmind DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
USE lawmind;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password VARCHAR(256) NOT NULL COMMENT '加密密码',
    nickname VARCHAR(64) COMMENT '昵称',
    phone VARCHAR(20) COMMENT '手机号',
    role VARCHAR(32) DEFAULT 'user' COMMENT '角色: admin/user',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_time DATETIME COMMENT '最后登录时间',
    is_deleted INT DEFAULT 0 COMMENT '软删除标记: 0-正常 1-已删除',
    UNIQUE INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(200) DEFAULT '新对话' COMMENT '会话标题',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0 COMMENT '软删除标记',
    INDEX idx_user_id (user_id),
    INDEX idx_update_time (user_id, update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- ============================================================
-- 3. 聊天记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_chat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT COMMENT '会话ID',
    user_question TEXT NOT NULL COMMENT '用户问题',
    ai_answer TEXT COMMENT 'AI回答',
    knowledge_match JSON COMMENT '匹配的知识数据(JSON)',
    token_usage_input INT COMMENT '输入Token数',
    token_usage_output INT COMMENT '输出Token数',
    estimated_cost DECIMAL(10,6) COMMENT '预估成本(元)',
    feedback INT COMMENT '用户反馈: 1=赞 -1=踩',
    feedback_content VARCHAR(500) COMMENT '反馈文字说明',
    feedback_status VARCHAR(20) DEFAULT NULL COMMENT '审核状态: PENDING_REVIEW/REVIEWED/DISMISSED',
    reviewed_by BIGINT COMMENT '审核人ID',
    reviewed_at DATETIME COMMENT '审核时间',
    review_notes VARCHAR(500) COMMENT '审核备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_feedback_status (feedback_status),
    INDEX idx_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';

-- ============================================================
-- 4. 法律知识库表
-- ============================================================
CREATE TABLE IF NOT EXISTS law_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '上传用户ID',
    law_type VARCHAR(64) COMMENT '法律类型: 民法/刑法/行政法等',
    title VARCHAR(500) NOT NULL COMMENT '法律文档标题',
    chapter VARCHAR(100) COMMENT '章',
    `section` VARCHAR(100) COMMENT '节',
    article_number INT COMMENT '条文号',
    content TEXT NOT NULL COMMENT '条文内容',
    vector_status INT DEFAULT 0 COMMENT '向量化状态: 0-未生成 1-已生成 2-失败',
    effective_date DATE COMMENT '生效日期',
    expiry_date DATE COMMENT '失效日期(NULL=无失效)',
    status VARCHAR(32) DEFAULT 'EFFECTIVE' COMMENT '法律状态: EFFECTIVE/REPEALED/DRAFT',
    source VARCHAR(64) DEFAULT 'BATCH_IMPORT' COMMENT '来源: BATCH_IMPORT/MANUAL/AUTO_LEARN',
    publisher VARCHAR(200) COMMENT '发布机构',
    publish_date DATE COMMENT '发布日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0 COMMENT '软删除标记',
    INDEX idx_law_type (law_type),
    INDEX idx_vector_status (vector_status),
    INDEX idx_status (status),
    INDEX idx_is_deleted (is_deleted),
    FULLTEXT INDEX ft_title_content (title, content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律知识库表';

-- ============================================================
-- 5. 知识块表（分块后的知识片段）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL COMMENT '所属法律知识ID',
    chunk_index INT NOT NULL COMMENT '块序号(从0开始)',
    context_prefix VARCHAR(500) COMMENT '上下文前缀,帮助定位法律出处',
    content TEXT NOT NULL COMMENT '块内容',
    vector_status INT DEFAULT 0 COMMENT '向量化状态: 0-未生成 1-已生成 2-失败',
    error_msg VARCHAR(500) COMMENT '向量化失败原因',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_vector_status (vector_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识块表';

-- ============================================================
-- 6. 向量任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS law_vector_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL COMMENT '关联知识ID',
    vector_status INT DEFAULT 0 COMMENT '向量状态: 0-待处理 1-已完成 2-失败',
    redis_search_sync INT DEFAULT 0 COMMENT 'Redis同步状态: 0-未同步 1-已同步',
    error_msg VARCHAR(500) COMMENT '失败原因',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_vector_status (vector_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量任务表';

-- ============================================================
-- 7. 文件上传表
-- ============================================================
CREATE TABLE IF NOT EXISTS law_file_upload (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '上传用户ID',
    knowledge_id BIGINT COMMENT '关联知识ID(解析入库后)',
    processing_status VARCHAR(32) DEFAULT 'PENDING' COMMENT '处理状态: PENDING/PROCESSING/COMPLETED/FAILED',
    file_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(32) COMMENT '文件类型: pdf/docx/txt',
    file_size BIGINT COMMENT '文件大小(字节)',
    file_path VARCHAR(1000) COMMENT '存储路径',
    content LONGTEXT COMMENT '解析后的文本内容',
    ai_review_result TEXT COMMENT 'AI审查结果',
    ai_revised_content TEXT COMMENT 'AI修订后内容',
    risk_level VARCHAR(20) COMMENT '风险等级: LOW/MEDIUM/HIGH/CRITICAL',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0 COMMENT '软删除标记',
    INDEX idx_user_id (user_id),
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_processing_status (processing_status),
    INDEX idx_upload_time (upload_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传表';

-- ============================================================
-- 8. 系统配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(200) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(500) COMMENT '配置说明',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- 9. 热点问题缓存表
-- ============================================================
CREATE TABLE IF NOT EXISTS hot_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_md5 CHAR(32) NOT NULL COMMENT '问题MD5(唯一标识)',
    original_question TEXT NOT NULL COMMENT '原始问题文本',
    cached_answer TEXT COMMENT '缓存的答案',
    knowledge_ids VARCHAR(500) COMMENT '关联知识点ID(逗号分隔)',
    visit_count INT DEFAULT 1 COMMENT '访问次数',
    first_visit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次访问时间',
    last_visit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME COMMENT '缓存过期时间',
    status INT DEFAULT 0 COMMENT '缓存状态: 0-有效 1-已过期 2-已淘汰',
    UNIQUE INDEX idx_question_md5 (question_md5),
    INDEX idx_status_expire (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热点问题缓存表';

-- ============================================================
-- 10. 安全审计日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS security_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '用户ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '操作类型',
    description VARCHAR(500) COMMENT '操作描述',
    resource_type VARCHAR(64) COMMENT '资源类型',
    resource_id BIGINT COMMENT '资源ID',
    request_method VARCHAR(10) COMMENT '请求方法: GET/POST/PUT/DELETE',
    request_uri VARCHAR(500) COMMENT '请求URI',
    request_params TEXT COMMENT '请求参数(JSON)',
    client_ip VARCHAR(64) COMMENT '客户端IP',
    request_id VARCHAR(64) COMMENT '请求追踪ID',
    result VARCHAR(20) COMMENT '操作结果: SUCCESS/FAIL',
    error_message TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_create_time (create_time DESC),
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计日志表';

-- ============================================================
-- 11. 反馈审核日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS review_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id BIGINT NOT NULL COMMENT '聊天记录ID',
    question TEXT COMMENT '关联问题',
    action_type VARCHAR(32) COMMENT '操作类型',
    action_detail VARCHAR(500) COMMENT '操作详情',
    feedback_reason VARCHAR(500) COMMENT '反馈原因',
    processed INT DEFAULT 0 COMMENT '处理状态: 0-未处理 1-已处理',
    processed_at DATETIME COMMENT '处理时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_processed (processed),
    INDEX idx_chat_id (chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈审核日志表';

-- ============================================================
-- 12. RAG指标日报表
-- ============================================================
CREATE TABLE IF NOT EXISTS rag_metrics_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_date DATE NOT NULL COMMENT '统计日期',
    total_requests BIGINT DEFAULT 0 COMMENT '总请求数',
    cache_hits BIGINT DEFAULT 0 COMMENT '缓存命中数',
    similar_hits BIGINT DEFAULT 0 COMMENT '相似问题命中数',
    knowledge_hits BIGINT DEFAULT 0 COMMENT '知识库命中数',
    llm_direct_count BIGINT DEFAULT 0 COMMENT 'LLM直接回答数',
    non_legal_count BIGINT DEFAULT 0 COMMENT '非法律问题拦截数',
    avg_latency_ms BIGINT DEFAULT 0 COMMENT '平均延迟(毫秒)',
    p50_latency_ms BIGINT DEFAULT 0 COMMENT 'P50延迟',
    p95_latency_ms BIGINT DEFAULT 0 COMMENT 'P95延迟',
    total_likes BIGINT DEFAULT 0 COMMENT '点赞总数',
    total_dislikes BIGINT DEFAULT 0 COMMENT '点踩总数',
    avg_top_score DECIMAL(5,4) COMMENT '平均Top-1得分',
    llm_fallback_rate DECIMAL(5,4) COMMENT 'LLM兜底率',
    hyde_count BIGINT DEFAULT 0 COMMENT 'HyDE调用次数',
    feedback_inaccurate BIGINT DEFAULT 0 COMMENT '反馈: 不准确',
    feedback_wrong_citation BIGINT DEFAULT 0 COMMENT '反馈: 法条引用错误',
    feedback_irrelevant BIGINT DEFAULT 0 COMMENT '反馈: 答非所问',
    feedback_too_vague BIGINT DEFAULT 0 COMMENT '反馈: 回答太笼统',
    feedback_other BIGINT DEFAULT 0 COMMENT '反馈: 其他问题',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_metric_date (metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG指标日报表';

-- ============================================================
-- 13. 评估报告表
-- ============================================================
CREATE TABLE IF NOT EXISTS evaluation_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL COMMENT '评估运行ID',
    dataset_path VARCHAR(500) COMMENT '数据集路径',
    dataset_version INT COMMENT '数据集版本',
    total_cases INT DEFAULT 0 COMMENT '总用例数',
    passed_cases INT DEFAULT 0 COMMENT '通过用例数',
    failed_cases INT DEFAULT 0 COMMENT '失败用例数',
    avg_keyword_recall DECIMAL(5,4) COMMENT '平均关键词召回率',
    avg_source_match DECIMAL(5,4) COMMENT '平均来源匹配率',
    avg_law_type_match DECIMAL(5,4) COMMENT '平均法律类型匹配率',
    avg_answer_length DECIMAL(10,2) COMMENT '平均回答长度',
    avg_total_score DECIMAL(5,4) COMMENT '平均总分',
    avg_faithfulness DECIMAL(5,4) COMMENT '平均忠实度',
    avg_answer_relevance DECIMAL(5,4) COMMENT '平均回答相关性',
    report_json JSON COMMENT '完整报告JSON',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_run_id (run_id),
    INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估报告表';

-- ============================================================
-- 14. 统一记忆表（四类型记忆模型）
-- ============================================================
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

-- ============================================================
-- 默认管理员账号
-- 用户名: admin  密码: 123456
-- ============================================================
INSERT IGNORE INTO `user` (username, password, nickname, role, create_time)
VALUES ('admin', '$2b$12$aknPp.rOwSHILZg1/avG0eHk1qhrARz3wMui3TjJuCpbq2c.zzjlm', '管理员', 'admin', NOW());
