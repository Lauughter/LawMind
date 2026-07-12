package com.lhs.lawmind.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传 → 解析 → 元数据提取 → 逐条拆分 → 入库 → 过长条文子分块
 */
public interface DocumentIngestionService {

    /**
     * @param fileContent 已提取的文件文本（由调用方提取，避免重复读取 MultipartFile）
     * @param fileName    文件名（用于推断法律名称）
     * @param userId      用户ID
     * @return 首条 LawKnowledge ID（用于文件关联）
     */
    Long ingest(String fileContent, String fileName, Long userId);
}
