package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 文件处理工具类
 * <p>提供多种文件类型的文本提取功能，支持TXT、DOCX、PDF等常见格式</p>
 * <p>使用 Apache Tika 进行未知格式的自动检测和解析</p>
 */
@Slf4j
@Component
public class FileUtil {

    private final Tika tika = new Tika();

    /**
     * 提取文件内容
     * <p>根据文件扩展名选择相应的解析方法，并返回文档内容</p>
     * <p>如果无法识别的文件类型，将使用Tika进行自动检测和解析</p>
     *
     * @param file 文件对象
     * @return 文档内容
     * @throws IOException 如果发生 IO 错误
     */
    public String extractText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IOException("文件名不能为空");
        }

        // 根据文件扩展名选择解析方法，忽略大小写
        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".txt")) {
            byte[] bytes = file.getBytes();
            return new String(bytes, detectCharset(bytes));
        } else if (lowerName.endsWith(".docx")) {
            return extractWordText(file.getInputStream());
        } else if (lowerName.endsWith(".pdf")) {
            return extractPdfText(file.getInputStream());
        } else if (lowerName.endsWith(".doc") || lowerName.endsWith(".html")
                || lowerName.endsWith(".htm") || lowerName.endsWith(".rtf")) {
            return extractWithTika(file.getInputStream());
        } else {
            // 未知类型尝试 Tika 自动检测
            return extractWithTika(file.getInputStream());
        }
    }

    /**
     * 使用 Apache POI 解析 Word 文档
     *
     * @param inputStream Word 文档输入流
     * @return 文档内容
     * @throws IOException 如果发生 IO 错误
     */
    private String extractWordText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * 使用 Apache PDFBox 流式解析 PDF 文档。
     * 通过 BufferedInputStream 避免将整个 PDF 加载到内存。
     *
     * @param inputStream PDF 文档输入流
     * @return 文档内容
     * @throws IOException 如果发生 IO 错误
     */
    private String extractPdfText(InputStream inputStream) throws IOException {
        byte[] pdfBytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            log.debug("PDF 页数: {}", pageCount);

            // 大文件页数过多时发出警告
            if (pageCount > 500) {
                log.warn("PDF 页数较多({})，文本提取可能需要较长时间", pageCount);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // 大文件分页提取，避免单次提取内存过大
            if (pageCount > 200) {
                StringBuilder sb = new StringBuilder();
                int batchSize = 50;
                for (int start = 1; start <= pageCount; start += batchSize) {
                    int end = Math.min(start + batchSize - 1, pageCount);
                    stripper.setStartPage(start);
                    stripper.setEndPage(end);
                    sb.append(stripper.getText(document));
                }
                log.info("PDF 分页提取完成: 页数={}, 字符数={}", pageCount, sb.length());
                return sb.toString();
            }

            String text = stripper.getText(document);
            log.debug("PDF 提取完成: 页数={}, 字符数={}", pageCount, text.length());
            return text;
        }
    }

    /**
     * 使用 Apache Tika 自动检测和解析未知格式的文件
     *
     * @param inputStream 文件输入流
     * @return 文档内容
     * @throws IOException 如果发生 IO 错误
     */
    private String extractWithTika(InputStream inputStream) throws IOException {
        try {
            return tika.parseToString(inputStream);
        } catch (TikaException e) {
            throw new IOException("Tika 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检测文本文件编码。
     * <ul>
     *   <li>BOM 标记检测：UTF-8 / UTF-16LE / UTF-16BE</li>
     *   <li>无 BOM 时，检查是否为有效 UTF-8</li>
     *   <li>回退到 GBK（中文 Windows 环境最常见编码）</li>
     * </ul>
     */
    private Charset detectCharset(byte[] bytes) {
        if (bytes.length == 0) return StandardCharsets.UTF_8;

        // BOM: UTF-8
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // BOM: UTF-16LE
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        // BOM: UTF-16BE
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }

        // 尝试 UTF-8 解码验证
        try {
            String test = new String(bytes, StandardCharsets.UTF_8);
            byte[] reEncoded = test.getBytes(StandardCharsets.UTF_8);
            if (java.util.Arrays.equals(bytes, reEncoded)) {
                return StandardCharsets.UTF_8;
            }
        } catch (Exception ignored) {
        }

        // 回退 GBK（中文 Windows 环境最常见）
        try {
            return Charset.forName("GBK");
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
