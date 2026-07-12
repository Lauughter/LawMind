package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.LawFileUpload;
import com.lhs.lawmind.service.AsyncVectorizeService;
import com.lhs.lawmind.service.DocumentIngestionService;
import com.lhs.lawmind.service.LawFileUploadService;
import com.lhs.lawmind.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    private final LawFileUploadService lawFileUploadService;
    private final DocumentIngestionService documentIngestionService;
    private final AsyncVectorizeService asyncVectorizeService;
    private final FileUtil fileUtil;

    public FileController(LawFileUploadService lawFileUploadService,
                          DocumentIngestionService documentIngestionService,
                          AsyncVectorizeService asyncVectorizeService,
                          FileUtil fileUtil) {
        this.lawFileUploadService = lawFileUploadService;
        this.documentIngestionService = documentIngestionService;
        this.asyncVectorizeService = asyncVectorizeService;
        this.fileUtil = fileUtil;
    }

    /**
     * 文件上传接口
     * <p>接收用户上传的文件，提取文本内容并保存到数据库</p>
     * <p>调用文档接入服务进行处理，并更新处理状态</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param file 用户上传的文件
     * @return Result对象，包含上传结果和状态码
     */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        try {
            String content = fileUtil.extractText(file);
            String fileName = file.getOriginalFilename();
            String fileType = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";

            LawFileUpload lawFileUpload = new LawFileUpload();
            lawFileUpload.setUserId(userId);
            lawFileUpload.setFileName(fileName);
            lawFileUpload.setFileType(fileType);
            lawFileUpload.setFileSize(file.getSize());
            lawFileUpload.setContent(content);
            lawFileUpload.setProcessingStatus("PROCESSING");
            lawFileUploadService.insert(lawFileUpload);

            try {
                Long knowledgeId = documentIngestionService.ingest(content, fileName, userId);
                lawFileUpload.setKnowledgeId(knowledgeId);
                lawFileUpload.setProcessingStatus("COMPLETED");
            } catch (Exception e) {
                log.error("文档接入失败: fileName={}, error={}", fileName, e.getMessage());
                lawFileUpload.setProcessingStatus("FAILED");
            }
            int updated = lawFileUploadService.update(lawFileUpload);
            if (updated == 0) {
                log.warn("文件状态更新失败：update 影响 0 行, id={}", lawFileUpload.getId());
            }
            if ("COMPLETED".equals(lawFileUpload.getProcessingStatus())) {
                asyncVectorizeService.batchVectorizeAsync(0, 200);
            }

            lawFileUpload.setContent(null);
            lawFileUpload.setAiReviewResult(null);
            lawFileUpload.setAiRevisedContent(null);
            return Result.success(lawFileUpload);
        } catch (IOException e) {
            return Result.error("文件处理失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户上传的文件列表
     * <p>根据用户ID查询上传的文件记录，并返回文件列表</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @return Result对象，包含文件列表和状态码
     */
    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        PageResult<LawFileUpload> pageResult = lawFileUploadService.selectByUserIdPage(userId, page, pageSize);
        pageResult.getList().forEach(f -> {
            f.setContent(null);
            f.setAiReviewResult(null);
            f.setAiRevisedContent(null);
        });
        return Result.success(pageResult);
    }

    /**
     * 获取文件内容接口
     * <p>根据文件ID查询文件记录，并返回文件内容</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 文件ID
     * @return Result对象，包含文件内容和状态码
     */
    @GetMapping("/content/{id}")
    public Result<?> getContent(@PathVariable Long id) {
        LawFileUpload file = lawFileUploadService.selectById(id);
        if (file == null) {
            return Result.error("文件不存在");
        }
        return Result.success(file.getContent());
    }

    @GetMapping("/info/{id}")
    public Result<?> getInfo(@PathVariable Long id) {
        LawFileUpload file = lawFileUploadService.selectById(id);
        if (file == null) {
            return Result.error("文件不存在");
        }
        file.setContent(null);
        file.setAiReviewResult(null);
        file.setAiRevisedContent(null);
        return Result.success(file);
    }

    /**
     * 删除文件接口
     * <p>根据文件ID删除文件记录</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 文件ID
     * @return Result对象，包含删除结果和状态码
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        lawFileUploadService.delete(id);
        return Result.success();
    }

    /**
     * 删除文件接口
     * <p>根据文件ID删除文件记录</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 文件ID
     * @return Result对象，包含删除结果和状态码
     */
    @DeleteMapping("/delete/{id}")
    public Result<?> deleteByPath(@PathVariable Long id) {
        lawFileUploadService.delete(id);
        return Result.success();
    }
}
