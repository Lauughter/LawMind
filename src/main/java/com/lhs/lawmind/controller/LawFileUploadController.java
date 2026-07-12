package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.entity.LawFileUpload;
import com.lhs.lawmind.service.DocumentIngestionService;
import com.lhs.lawmind.service.LawFileUploadService;
import com.lhs.lawmind.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/law-file-upload")
public class LawFileUploadController {

    private final LawFileUploadService lawFileUploadService;
    private final DocumentIngestionService documentIngestionService;
    private final FileUtil fileUtil;

    public LawFileUploadController(LawFileUploadService lawFileUploadService,
                                   DocumentIngestionService documentIngestionService,
                                   FileUtil fileUtil) {
        this.lawFileUploadService = lawFileUploadService;
        this.documentIngestionService = documentIngestionService;
        this.fileUtil = fileUtil;
    }

    /**
     * 查询所有上传的法律文件
     * <p>返回所有上传的法律文件列表</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含数据列表和状态码</p>
     *
     * @return Result对象，包含法律文件列表和状态码
     */
    @GetMapping("/list")
    public Result<PageResult<LawFileUpload>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(lawFileUploadService.selectPage(page, pageSize));
    }

    @GetMapping("/list-by-user/{userId}")
    public Result<PageResult<LawFileUpload>> listByUser(@PathVariable Long userId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(lawFileUploadService.selectByUserIdPage(userId, page, pageSize));
    }

    /**
     * 根据ID查询上传的法律文件
     * <p>根据法律文件的唯一标识ID查询完整的法律文件信息</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含法律文件实体和状态码</p>
     *
     * @param id
     * @return
     */
    @GetMapping("/get/{id}")
    public Result<LawFileUpload> get(@PathVariable Long id) {
        return Result.success(lawFileUploadService.selectById(id));
    }

    /**
     * 添加新的法律文件上传记录
     * <p>接收法律文件上传信息，保存到数据库</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含状态码</p>
     *
     * @param lawFileUpload
     * @return
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody LawFileUpload lawFileUpload) {
        lawFileUploadService.insert(lawFileUpload);
        return Result.success();
    }

    /**
     * 更新法律文件上传记录
     * <p>接收更新后的法律文件上传信息，更新数据库记录</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含状态码</p>
     *
     * @param lawFileUpload
     * @return
     */
    @PostMapping("/update")
    public Result<?> update(@RequestBody LawFileUpload lawFileUpload) {
        lawFileUploadService.update(lawFileUpload);
        return Result.success();
    }

    /**
     * 删除法律文件上传记录
     * <p>根据法律文件上传记录的唯一标识ID删除记录</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含状态码</p>
     *
     * @param id
     * @return
     */
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        lawFileUploadService.delete(id);
        return Result.success();
    }

    /**
     * 删除用户上传的文件（带认证的接口，用于个人中心）
     * <p>根据法律文件上传记录的唯一标识ID删除记录</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含状态码</p>
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteFromProfile(@PathVariable Long id) {
        lawFileUploadService.delete(id);
        return Result.success();
    }

    /**
     * 文件上传接口
     * <p>接收文件上传请求，提取文件内容，保存到数据库，并触发文档接入流程</p>
     * <p>包含异常处理和日志记录</p>
     * <p>返回结果为Result对象，包含上传记录和状态码</p>
     * @param file
     * @param userId
     * @return
     */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file, @RequestParam("userId") Long userId) {
        try {
            // 提取文件内容
            String content = fileUtil.extractText(file);
            String fileName = file.getOriginalFilename();
            String fileType = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";

            // 保存到数据库
            LawFileUpload lawFileUpload = new LawFileUpload();
            lawFileUpload.setUserId(userId);
            lawFileUpload.setFileName(fileName);
            lawFileUpload.setFileType(fileType);
            lawFileUpload.setFileSize(file.getSize());
            lawFileUpload.setContent(content);
            lawFileUpload.setProcessingStatus("PROCESSING");
            lawFileUploadService.insert(lawFileUpload);

            // 触发文档接入流水线：解析 → 元数据提取 → 分块 → 创建知识
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

            return Result.success(lawFileUpload);
        } catch (IOException e) {
            return Result.error("文件处理失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }
}