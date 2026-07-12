package com.lhs.lawmind.controller;

import com.lhs.lawmind.service.AsyncVectorizeService;
import com.lhs.lawmind.service.LawVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 法律知识向量化任务控制器
 * 提供手动触发单条、批量和全量向量化的接口
 * 包含向量化进度查询接口（预留）
 * 使用异步服务执行向量化任务，避免阻塞请求线程
 */
@Slf4j
@RestController
@RequestMapping("/api/vectorize")
public class LawVectorTaskController {

    private final AsyncVectorizeService asyncVectorizeService;
    private final LawVectorService lawVectorService;

    public LawVectorTaskController(AsyncVectorizeService asyncVectorizeService, LawVectorService lawVectorService) {
        this.asyncVectorizeService = asyncVectorizeService;
        this.lawVectorService = lawVectorService;
    }

    /**
     * 手动触发单条数据向量化
     * @param id 法律知识库ID
     * @return 响应信息
     */
    @PostMapping("/single/{id}")
    public ResponseEntity<?> vectorizeSingle(@PathVariable Long id) {
        asyncVectorizeService.vectorizeAsync(id);
        return ResponseEntity.ok("向量化任务已启动，请查看日志");
    }

    /**
     * 手动触发批量数据向量化
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 响应信息
     */
    @PostMapping("/batch")
    public ResponseEntity<?> vectorizeBatch(@RequestParam int offset, @RequestParam int limit) {
        asyncVectorizeService.batchVectorizeAsync(offset, limit);
        return ResponseEntity.ok("批量向量化任务已启动，请查看日志");
    }

    /**
     * 手动触发全量向量化（分批处理）
     * @param batchSize 每批处理数量，默认100
     * @return 响应信息
     */
    @PostMapping("/all")
    public ResponseEntity<?> vectorizeAll(@RequestParam(defaultValue = "100") int batchSize) {
        // 启动异步任务，分批处理所有数据
        new Thread(() -> {
            int offset = 0;
            int totalProcessed = 0;
            boolean hasMore = true;
            
            while (hasMore) {
                log.info("【全量向量化】处理第 {} 批数据, offset={}, limit={}", (offset / batchSize) + 1, offset, batchSize);
                
                int processed = lawVectorService.batchVectorize(offset, batchSize);
                totalProcessed += processed;
                
                log.info("【全量向量化】第 {} 批处理完成, 本批处理 {} 条, 累计处理 {} 条", (offset / batchSize) + 1, processed, totalProcessed);
                
                if (processed < batchSize) {
                    hasMore = false;
                    log.info("【全量向量化】全量向量化完成, 总共处理 {} 条数据", totalProcessed);
                } else {
                    offset += batchSize;
                    // 短暂休眠，避免系统过载
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }).start();
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "全量向量化任务已启动，请查看日志");
        result.put("batchSize", batchSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询向量化进度（预留接口，后续可实现）
     * @return 进度信息
     */
    @GetMapping("/progress")
    public ResponseEntity<?> getProgress() {
        // TODO: 实现进度查询功能
        Map<String, Object> progress = new HashMap<>();
        progress.put("status", "running");
        progress.put("message", "向量化任务正在执行中，请查看日志了解详情");
        return ResponseEntity.ok(progress);
    }
}
