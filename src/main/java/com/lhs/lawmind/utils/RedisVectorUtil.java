package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis 向量工具类
 * 负责向量与字节数组的转换、RedisSearch KNN 向量检索
 * 提供向量存储、读取、删除和搜索功能，支持向量与元数据的统一存储格式
 * 适用于法律知识库中的向量数据管理和相似度搜索
 * <p>向量存储使用 Redis Hash，字段包括 "vector"（原始字节数组）、"law_type"、"title"、"content" 等，所有字段统一使用原始字节存储，避免序列化不一致导致的数据读取问题</p>
 * <p>向量搜索使用 RedisSearch 的 FT.SEARCH 命令，支持 KNN 搜索和 key 前缀过滤，返回搜索结果列表，包括 key 和相似度（基于余弦距离转换）</p>
 * <p>提供了向量与字节数组的转换方法，支持 float 数组与 FLOAT32 小端字节数组之间的转换，确保向量数据在 Redis 中的高效存储和准确读取</p>
 * <p>包含异常处理和日志记录，帮助调试和监控向量存储和搜索过程中的问题</p>
 */
@Slf4j
@Component
public class RedisVectorUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisVectorUtil(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    /**
     * 将余弦距离 (0-2) 转换为相似度 (0-1)
     * <p>余弦距离 0 = 完全相似 → 相似度 1.0</p>
     * <p>余弦距离 2 = 完全不相似 → 相似度 0.0</p>
     */
    public static double cosineDistanceToSimilarity(double cosineDistance) {
        return Math.max(0.0, Math.min(1.0, 1.0 - (cosineDistance / 2.0)));
    }

    /**
     * 将 float 数组转换为 FLOAT32 小端字节数组
     */
    public static byte[] floatArrayToBytes(float[] floatArray) {
        if (floatArray == null || floatArray.length == 0) {
            return new byte[0];
        }
        // 使用 ByteBuffer 进行高效的 float 到 byte 的转换，确保小端序
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float value : floatArray) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    /**
     * 从 RedisSearch 响应中提取 score 字段值
     * <p>响应格式: [field1, value1, field2, value2, ...]</p>
     *
     * @param fieldsList RediSearch 返回的字段列表
     * @return score 值 (余弦距离)，未找到返回 0.0
     */
    public static double extractScoreFromFields(List<?> fieldsList) {
        for (int i = 0; i < fieldsList.size() - 1; i++) {
            Object fieldObj = fieldsList.get(i);
            String fieldName = null;
            if (fieldObj instanceof byte[]) {
                fieldName = new String((byte[]) fieldObj);
            } else if (fieldObj instanceof String) {
                fieldName = (String) fieldObj;
            }
            if ("score".equals(fieldName) && i + 1 < fieldsList.size()) {
                return parseScoreValue(fieldsList.get(i + 1));
            }
        }
        return 0.0;
    }

    /**
     * 从字段值中提取 key（字节数组 → 字符串）
     * <p>RedisSearch 返回的 key 可能是 byte[] 或 String 类型，统一转换为 String</p>
     * @param keyObj RedisSearch 返回的 key 对象
     * @return 转换后的 key 字符串，无法转换返回 null
     */
    public static String keyFromObject(Object keyObj) {
        if (keyObj instanceof byte[]) {
            return new String((byte[]) keyObj);
        } else if (keyObj instanceof String) {
            return (String) keyObj;
        }
        return null;
    }

    /**
     * 从字段值中提取 score 值
     * <p>RedisSearch 响应的字段值可能是 Double、Float、String、byte[] 等类型，统一转换为 double</p>
     * @param scoreObj RedisSearch 响应的字段值对象
     * @return 提取的 score 值，无法提取返回 0.0
     */
    private static double parseScoreValue(Object scoreObj) {
        if (scoreObj instanceof Double) return (Double) scoreObj;
        if (scoreObj instanceof Float) return ((Float) scoreObj).doubleValue();
        if (scoreObj instanceof String) {
            try {
                return Double.parseDouble((String) scoreObj);
            } catch (NumberFormatException ignored) {}
        }
        if (scoreObj instanceof byte[]) {
            try {
                return Double.parseDouble(new String((byte[]) scoreObj));
            } catch (NumberFormatException ignored) {}
        }
        log.warn("无法解析 score 值: type={}", scoreObj != null ? scoreObj.getClass().getName() : "null");
        return 0.0;
    }

    /**
     * 存储向量数据到 Redis
     * <p>向量数据存储使用 Redis Hash，字段包括 "vector"（原始字节数组）</p>
     * <p>所有字段统一使用原始字节存储，避免序列化不一致导致的数据读取问题</p>
     * @param key 存储的 key
     * @param vector 存储的向量数据
     */
    public void storeVector(String key, float[] vector) {
        log.debug("存储向量: {}", key);
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null");
            return;
        }
        try {
            byte[] bytes = floatArrayToBytes(vector);
            redisTemplate.opsForHash().put(key, "vector", bytes);
        } catch (Exception e) {
            log.error("存储向量失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 从 Redis 读取向量数据
     * <p>读取时尝试多种类型转换，优先使用原始字节数组，兼容旧数据格式</p>
     * <p>如果读取失败或数据格式不正确，返回 null</p>
     * @param key
     * @return 读取的向量数据，失败返回 null
     */
    public float[] getVector(String key) {
        log.debug("读取向量: {}", key);
        if (redisTemplate == null) return null;
        try {
            Object obj = redisTemplate.opsForHash().get(key, "vector");
            if (obj == null) return null;
            if (obj instanceof float[]) return (float[]) obj;
            if (obj instanceof byte[]) return bytesToFloatArray((byte[]) obj);
            if (obj instanceof String) return parseVectorString((String) obj);
            log.warn("未知向量类型: {}", obj.getClass().getName());
            return null;
        } catch (Exception e) {
            log.error("读取向量失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除向量数据
     * @param key
     */
    public void deleteVector(String key) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("删除向量失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 存储向量及元数据到 Redis Hash（所有字段使用原始字节，统一序列化路径）
     *
     * @param key          存储的 key
     * @param vector       向量数据
     * @param lawType      法律类型
     * @param title        标题
     * @param content      内容
     * @param chapter      章标题（可为 null）
     * @param section      节标题（可为 null）
     * @param articleNumber 条文序号（可为 null）
     * @param contextPrefix 上下文前缀（可为 null）
     */
    public void storeVectorWithMetadata(String key, float[] vector, String lawType, String title,
                                        String content, String chapter, String section,
                                        Integer articleNumber, String contextPrefix) throws Exception {
        if (redisTemplate == null) throw new Exception("RedisTemplate is null");
        try {
            byte[] vectorBytes = floatArrayToBytes(vector);
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                byte[] kb = key.getBytes();
                connection.hSet(kb, "vector".getBytes(), vectorBytes);
                connection.hSet(kb, "law_type".getBytes(), lawType != null ? lawType.getBytes() : new byte[0]);
                connection.hSet(kb, "title".getBytes(), title != null ? title.getBytes() : new byte[0]);
                connection.hSet(kb, "content".getBytes(), content != null ? content.getBytes() : new byte[0]);
                connection.hSet(kb, "chapter".getBytes(), chapter != null ? chapter.getBytes() : new byte[0]);
                connection.hSet(kb, "section".getBytes(), section != null ? section.getBytes() : new byte[0]);
                connection.hSet(kb, "article_number".getBytes(), articleNumber != null ? String.valueOf(articleNumber).getBytes() : new byte[0]);
                connection.hSet(kb, "context_prefix".getBytes(), contextPrefix != null ? contextPrefix.getBytes() : new byte[0]);
                return null;
            });
            log.debug("存储向量及元数据成功: {}", key);
        } catch (Exception e) {
            log.error("存储向量及元数据失败: key={}, error={}", key, e.getMessage());
            throw e;
        }
    }

    /**
     * KNN 向量搜索
     *
     * @param indexName RediSearch 索引名
     * @param vector    查询向量
     * @param topK      返回数量
     * @return 搜索结果列表 (key + 相似度)
     */
    public List<SearchResult> searchSimilar(String indexName, float[] vector, int topK) {
        return searchSimilar(indexName, vector, topK, null);
    }

    /**
     * KNN 向量搜索（带 key 前缀过滤）
     *
     * @param indexName RediSearch 索引名
     * @param vector    查询向量
     * @param topK      返回数量
     * @param keyPrefix 仅返回以该前缀开头的 key（null 表示不过滤）
     * @return 搜索结果列表
     */
    public List<SearchResult> searchSimilar(String indexName, float[] vector, int topK, String keyPrefix) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法执行向量搜索");
            return new ArrayList<>();
        }
        if (vector == null || vector.length == 0) {
            log.warn("向量为空，无法搜索");
            return new ArrayList<>();
        }

        try {
            byte[] vectorBytes = floatArrayToBytes(vector);

            return redisTemplate.execute((RedisCallback<List<SearchResult>>) connection -> {
                List<SearchResult> results = new ArrayList<>();
                try {
                    String knnQuery = "*=>[KNN " + topK + " @vector $vec AS score]";
                    byte[][] args = new byte[][]{
                            indexName.getBytes(), knnQuery.getBytes(),
                            "DIALECT".getBytes(), "2".getBytes(),
                            "PARAMS".getBytes(), "2".getBytes(),
                            "vec".getBytes(), vectorBytes,
                            "LIMIT".getBytes(), "0".getBytes(), String.valueOf(topK).getBytes()
                    };

                    Object result = connection.execute("FT.SEARCH", args);

                    if (result instanceof List<?> respList) {
                        parseSearchResults(respList, results, keyPrefix);
                    } else if (result instanceof Object[] respArray) {
                        parseLegacyResults(respArray, results, keyPrefix);
                    }

                    log.debug("向量搜索完成: index={}, hits={}", indexName, results.size());
                } catch (Exception e) {
                    log.error("FT.SEARCH 执行失败: index={}, error={}", indexName, e.getMessage());
                }
                return results;
            });
        } catch (Exception e) {
            log.error("向量搜索失败: index={}, error={}", indexName, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析 RedisSearch 返回的搜索结果列表
     * <p>兼容 RedisTemplate 不同版本返回的 List<?> 和 Object[] 两种格式</p>
     * @param respList RedisSearch 返回的结果列表
     * @param results 解析后的搜索结果列表
     * @param keyPrefix key 前缀过滤，null 表示不过滤
     */
    private void parseSearchResults(List<?> respList, List<SearchResult> results, String keyPrefix) {
        for (int i = 1; i < respList.size(); i += 2) {
            String key = keyFromObject(respList.get(i));
            if (key == null || (keyPrefix != null && !key.startsWith(keyPrefix))) continue;
            if (i + 1 < respList.size() && respList.get(i + 1) instanceof List<?> fieldsList) {
                double score = extractScoreFromFields(fieldsList);
                results.add(new SearchResult(key, cosineDistanceToSimilarity(score)));
            }
        }
    }

    /**
     * 解析 RedisSearch 旧版本返回的搜索结果列表
     * <p>兼容 RedisTemplate 旧版本返回的 Object[] 列表</p>
     * @param respArray RedisSearch 旧版本返回的结果列表
     * @param results 解析后的搜索结果列表
     * @param keyPrefix key 前缀过滤，null 表示不过滤
     */
    private void parseLegacyResults(Object[] respArray, List<SearchResult> results, String keyPrefix) {
        for (int i = 1; i < respArray.length; i += 2) {
            String key = keyFromObject(respArray[i]);
            if (key == null || (keyPrefix != null && !key.startsWith(keyPrefix))) continue;
            double score = 0.0;
            if (i + 1 < respArray.length) score = parseScoreValue(respArray[i + 1]);
            results.add(new SearchResult(key, cosineDistanceToSimilarity(score)));
        }
    }

    /**
     * 将 FLOAT32 小端字节数组转换为 float 数组
     * @param bytes 输入的字节数组，长度必须是 4 的倍数
     * @return 转换后的 float 数组，输入无效返回 null
     */
    private float[] bytesToFloatArray(byte[] bytes) {
        if (bytes.length % 4 != 0) return null;
        float[] vector = new float[bytes.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * 将字符串表示的向量转换为 float 数组
     * @param str 输入的字符串，格式为 "x,y,z"，每个元素都是 float
     * @return 转换后的 float 数组，输入无效返回 null
     */
    private float[] parseVectorString(String str) {
        if (str.isEmpty()) return null;
        String[] parts = str.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    // ==================== 内部类 ====================

    /**
     * 搜索结果类，包含 key 和相似度 score
     */
    public static class SearchResult {
        private final String key;
        private final double score;

        public SearchResult(String key, double score) {
            this.key = key;
            this.score = score;
        }

        public String getKey() { return key; }
        public double getScore() { return score; }
    }
}
