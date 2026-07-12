package com.lhs.lawmind.controller;

import com.lhs.lawmind.aop.annotation.RateLimit;
import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.service.LawVectorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/law-knowledge")
public class LawKnowledgeController {

    private final LawKnowledgeService lawKnowledgeService;
    private final LawVectorService lawVectorService;

    public LawKnowledgeController(LawKnowledgeService lawKnowledgeService,
                                  LawVectorService lawVectorService) {
        this.lawKnowledgeService = lawKnowledgeService;
        this.lawVectorService = lawVectorService;
    }

    /**
     * 列出法律知识
     * <p>支持分页、关键词搜索和类型过滤</p>
     * <p>根据请求参数动态构建查询条件，返回符合条件的法律知识列表和总记录数</p>
     *
     * @param page 当前页码，默认为1
     * @param pageSize 每页记录数，默认为10
     * @param keyword 搜索关键词，可选
     * @param type 法律知识类型过滤，可选
     * @return Result对象，包含法律知识列表、总记录数和状态码
     */
    @RateLimit(limit = 60, windowSeconds = 60)
    @GetMapping("/list")
    public Result<PageResult<LawKnowledge>> list(@RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int pageSize,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String type) {
        List<LawKnowledge> list;
        long total;

        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        boolean hasType = type != null && !type.isEmpty();

        if (hasKeyword && hasType) {
            list = lawKnowledgeService.searchByKeywordAndType(keyword, type, page, pageSize);
            total = lawKnowledgeService.countByKeywordAndType(keyword, type);
        } else if (hasKeyword) {
            list = lawKnowledgeService.search(keyword, page, pageSize);
            total = lawKnowledgeService.countByKeyword(keyword);
        } else if (hasType) {
            list = lawKnowledgeService.selectByLawType(type, page, pageSize);
            total = lawKnowledgeService.countByLawType(type);
        } else {
            list = lawKnowledgeService.selectPage(page, pageSize);
            total = lawKnowledgeService.count();
        }

        return Result.success(PageResult.of(total, list, page, pageSize));
    }

    /**
     * 按法律类型分页列表
     *
     * @param lawType 法规类型
     * @param page 当前页码，默认为1
     * @param pageSize 每页记录数，默认为10
     */
    @GetMapping("/list-by-law-type/{lawType}")
    public Result<PageResult<LawKnowledge>> listByLawType(@PathVariable String lawType,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int pageSize) {
        List<LawKnowledge> list = lawKnowledgeService.selectByLawType(lawType, page, pageSize);
        long total = lawKnowledgeService.countByLawType(lawType);
        return Result.success(PageResult.of(total, list, page, pageSize));
    }

    /**
     * 搜索法律知识
     *
     * @param keyword 搜索关键词
     * @param page 当前页码，默认为1
     * @param pageSize 每页记录数，默认为10
     */
    @RateLimit(limit = 30, windowSeconds = 60)
    @GetMapping("/search")
    public Result<PageResult<LawKnowledge>> search(@RequestParam String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int pageSize) {
        List<LawKnowledge> list = lawKnowledgeService.search(keyword, page, pageSize);
        long total = lawKnowledgeService.countByKeyword(keyword);
        return Result.success(PageResult.of(total, list, page, pageSize));
    }

    /**
     * 获取法律知识详情
     *
     * @param id 法规ID
     * @return Result对象，包含法律知识详情和状态码
     */
    @GetMapping("/get/{id}")
    public Result<LawKnowledge> get(@PathVariable Long id) {
        return Result.success(lawKnowledgeService.selectById(id));
    }

    /**
     * 新增法律知识
     *
     * @param lawKnowledge 法规对象
     * @return Result对象，包含状态码
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody LawKnowledge lawKnowledge) {
        lawKnowledge.setUserId(RequestContext.getUserId());
        lawKnowledgeService.insert(lawKnowledge);
        return Result.success();
    }

    /**
     * 更新法律知识
     *
     * @param lawKnowledge 法规对象
     * @return Result对象，包含状态码
     */
    @PostMapping("/update")
    public Result<?> update(@RequestBody LawKnowledge lawKnowledge) {
        lawKnowledgeService.update(lawKnowledge);
        return Result.success();
    }

    /**
     * 删除法律知识
     *
     * @param id 法规ID
     * @return Result对象，包含状态码
     */
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        lawKnowledgeService.delete(id);
        return Result.success();
    }

    /**
     * 向量化法律知识
     * <p>将法律知识向量化，并保存向量结果</p>
     *
     * @return Result对象，包含状态码
     */
    @PostMapping("/vectorize")
    public Result<?> vectorize() {
        lawKnowledgeService.vectorizeAndStore();
        return Result.success();
    }

    /**
     * 向量化所有法律知识
     * <p>将所有法律知识向量化，并保存向量结果</p>
     *
     * @return Result对象，包含状态码
     */
    @PostMapping("/api/vectorize/all")
    public String vectorizeAll() {
        lawKnowledgeService.vectorizeAndStore();
        return "向量化任务已启动，请查看日志";
    }

    /**
     * 批量向量化法律知识
     * <p>将法律知识向量化，并保存向量结果</p>
     *
     * @return Result对象，包含状态码
     */
    @PostMapping("/api/vectorize/batch")
    public String vectorizeBatch() {
        int successCount = lawVectorService.batchVectorize();
        return "批量向量化任务已完成，成功处理 " + successCount + " 条数据";
    }

    /**
     * 批量向量化法律知识（带偏移量和限制）
     * <p>将法律知识向量化，并保存向量结果</p>
     *
     * @param offset 起始偏移量
     * @param limit 处理记录数限制
     * @return Result对象，包含状态码
     */
    @PostMapping("/api/vectorize/batch/{offset}/{limit}")
    public String vectorizeBatchWithOffset(@PathVariable int offset, @PathVariable int limit) {
        int successCount = lawVectorService.batchVectorize(offset, limit);
        return "批量向量化任务已完成，成功处理 " + successCount + " 条数据";
    }
}