package com.lhs.lawmind.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhs.lawmind.common.BusinessException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * 加载 Golden Dataset JSON 文件
 * Golden Dataset JSON 文件作用：
 * 1. 提供评测所需的标准问题和答案数据。
 * 2. 作为评测的基准数据集，确保评测结果的一致性和可比性。
 * 3. 支持评测系统的自动化测试和验证，帮助开发者快速发现和修复问题。
 * 4. 促进评测结果的分析和改进，推动系统性能的提升。
 * 5. 作为评测结果的参考，帮助用户理解系统的表现和改进空间。
 */
public class GoldenDatasetLoader {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DEFAULT_PATH = "docs/golden-dataset-rag-evaluation.json";

    public List<GoldenDatasetRecord> load() {
        return load(DEFAULT_PATH);
    }

    public List<GoldenDatasetRecord> load(String path) {
        File file = new File(path);
        if (file.exists()) {
            try {
                return mapper.readValue(file, new TypeReference<List<GoldenDatasetRecord>>() {});
            } catch (IOException e) {
                throw BusinessException.serviceError("数据集加载失败: " + path);
            }
        }
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is != null) {
            try {
                return mapper.readValue(is, new TypeReference<List<GoldenDatasetRecord>>() {});
            } catch (IOException e) {
                throw BusinessException.serviceError("数据集加载失败: " + path);
            }
        }
        return Collections.emptyList();
    }
}
