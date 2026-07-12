package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.KnowledgeChunk;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.event.KnowledgeCreatedEvent;
import com.lhs.lawmind.mapper.KnowledgeChunkMapper;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.DocumentIngestionService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.config.ChunkingProperties;
import com.lhs.lawmind.utils.FixedWindowChunker;
import com.lhs.lawmind.utils.LegalArticleChunker;
import com.lhs.lawmind.utils.LegalDocumentNode;
import com.lhs.lawmind.utils.LegalMetadataExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 文档接入服务实现（优化版）
 *
 * <p>处理流程：
 * <ol>
 *   <li>文件文本提取</li>
 *   <li>元数据提取（法律类型、发布者、发布日期）</li>
 *   <li>文档树解析 —— 构建 章→节→条→款 的层级结构</li>
 *   <li>序言按段落分块入库（如有）</li>
 *   <li>每条条文入库（含章/节/条路径信息）</li>
 *   <li>长条文子分块，每块携带上下文前缀</li>
 * </ol>
 */
@Slf4j
@Service
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final int subChunkThreshold;

    private final LegalMetadataExtractor metadataExtractor;
    private final LegalArticleChunker articleChunker;
    private final FixedWindowChunker windowChunker;
    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final LawKnowledgeService lawKnowledgeService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentIngestionServiceImpl(LegalMetadataExtractor metadataExtractor,
                                        LegalArticleChunker articleChunker,
                                        FixedWindowChunker windowChunker,
                                        ChunkingProperties chunkingProps,
                                        LawKnowledgeMapper lawKnowledgeMapper,
                                        KnowledgeChunkMapper knowledgeChunkMapper,
                                        LawKnowledgeService lawKnowledgeService,
                                        ApplicationEventPublisher eventPublisher) {
        this.metadataExtractor = metadataExtractor;
        this.articleChunker = articleChunker;
        this.windowChunker = windowChunker;
        this.subChunkThreshold = chunkingProps.getSubChunkThreshold();
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.lawKnowledgeService = lawKnowledgeService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ingest(String fileContent, String fileName, Long userId) {
        log.info("[文档接入] 开始处理: fileName={}, userId={}", fileName, userId);
        log.info("[文档接入] 文本长度={}", fileContent.length());

        // 2. 元数据提取
        LegalMetadataExtractor.MetadataResult meta = metadataExtractor.extract(fileContent, fileName);
        String lawType = meta.lawType() != null ? meta.lawType() : "通用法律";
        String lawName = extractLawName(fileName);
        log.info("[文档接入] 元数据: lawType={}, lawName={}, publisher={}, docType={}, level={}",
                lawType, lawName, meta.publisher(), meta.documentType(), meta.effectivenessLevel());

        // 3. 文档树解析（传入 lawName，确保上下文前缀包含法律名称）
        LegalDocumentNode.LegalDocument doc = articleChunker.parse(fileContent, lawName);
        log.info("[文档接入] 文档树解析完成: 章={}, 条={}",
                doc.getChapters().size(), doc.getAllArticles().size());

        Long firstKnowledgeId = null;
        int totalChunks = 0;

        // 4. 处理序言
        if (doc.hasPreamble()) {
            List<String> preambleChunks = windowChunker.chunkWithPrefix(
                    doc.getPreamble(), "[" + lawName + " 序言]");
            for (String pc : preambleChunks) {
                LawKnowledge k = createKnowledgeRecord(lawType, lawName + " 序言",
                        null, null, null, pc, userId, meta);
                lawKnowledgeService.insert(k);
                if (firstKnowledgeId == null) firstKnowledgeId = k.getId();
                // 序言分块超过阈值时子分块
                if (pc.length() > subChunkThreshold) {
                    List<String> subs = windowChunker.chunkWithPrefix(pc,
                            "[" + lawName + " 序言]");
                    totalChunks += insertSubChunks(k.getId(), subs,
                            "[" + lawName + " 序言]");
                }
            }
        }

        // 5. 处理条文
        List<LegalDocumentNode.ArticleNode> allArticles = doc.getAllArticles();
        for (LegalDocumentNode.ArticleNode article : allArticles) {
            String title = buildArticleTitle(lawName, article);
            String content = article.getContent();

            LawKnowledge knowledge = createKnowledgeRecord(lawType, title,
                    article.getChapterPath(), article.getSectionPath(),
                    article.getArticleNum(), content, userId, meta);
            lawKnowledgeService.insert(knowledge);

            if (firstKnowledgeId == null) firstKnowledgeId = knowledge.getId();

            // 6. 长条文子分块（带上下文前缀）
            if (content.length() > subChunkThreshold) {
                String prefix = article.getContextPrefix();
                List<String> subChunks = windowChunker.chunkWithPrefix(content, prefix);
                totalChunks += insertSubChunks(knowledge.getId(), subChunks, prefix);
            }
        }

        log.info("[文档接入] 完成: 条数={}, 分块数={}", allArticles.size(), totalChunks);

        // 发布事件，事务提交后由监听器异步触发向量化
        eventPublisher.publishEvent(new KnowledgeCreatedEvent("DocumentIngestion:" + lawName));

        return firstKnowledgeId;
    }

    // ──────────── 辅助方法 ────────────

    private String extractLawName(String fileName) {
        String name = fileName.replaceAll("\\.[^.]+$", ""); // 去扩展名
        name = name.replaceAll("[（(]\\d{4}.*?[)）]", "");   // 去年份后缀
        name = name.replaceAll("全文|修正版|修订版|修正文本", "");
        return name.trim();
    }

    private LawKnowledge createKnowledgeRecord(String lawType, String title,
                                                String chapter, String section,
                                                Integer articleNumber,
                                                String content, Long userId,
                                                LegalMetadataExtractor.MetadataResult meta) {
        LawKnowledge k = new LawKnowledge();
        k.setLawType(lawType);
        k.setTitle(title);
        k.setChapter(chapter);
        k.setSection(section);
        k.setArticleNumber(articleNumber);
        k.setContent(content);
        k.setUserId(userId);
        k.setVectorStatus(0);
        k.setStatus("EFFECTIVE");
        k.setSource("BATCH_IMPORT");
        k.setPublisher(meta.publisher());
        k.setPublishDate(meta.publishDate());
        k.setCreateTime(new Date());
        return k;
    }

    private String buildArticleTitle(String lawName, LegalDocumentNode.ArticleNode article) {
        StringBuilder sb = new StringBuilder(lawName);
        // 提取章标题的简化版
        if (article.getChapterPath() != null && !article.getChapterPath().isBlank()) {
            // chapterPath 格式: "网络安全法 第一章 总纲"
            String[] parts = article.getChapterPath().split(" ", 3);
            if (parts.length >= 2) sb.append(" ").append(parts[parts.length - 1]);
        }
        if (article.getSectionPath() != null
                && !article.getSectionPath().equals(article.getChapterPath())) {
            String[] parts = article.getSectionPath().split(" ");
            String lastPart = parts[parts.length - 1];
            if (!lastPart.equals(sb.toString())) {
                sb.append(" ").append(lastPart);
            }
        }
        sb.append(" ").append(article.getHeading());
        return sb.toString();
    }

    private int insertSubChunks(Long knowledgeId, List<String> subChunks, String contextPrefix) {
        for (int j = 0; j < subChunks.size(); j++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKnowledgeId(knowledgeId);
            chunk.setChunkIndex(j);
            chunk.setContent(subChunks.get(j));
            chunk.setContextPrefix(contextPrefix);
            chunk.setVectorStatus(0);
            chunk.setRetryCount(0);
            knowledgeChunkMapper.insert(chunk);
        }
        return subChunks.size();
    }
}
