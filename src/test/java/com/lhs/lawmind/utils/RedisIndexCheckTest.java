package com.lhs.lawmind.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis索引检查测试类
 * 用于检查RedisSearch中的向量索引状态
 */
@SpringBootTest
public class RedisIndexCheckTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void testCheckRedisIndexes() {
        System.out.println("=== 开始检查RedisSearch索引 ===");

        if (redisTemplate == null) {
            System.out.println("RedisTemplate is null, 无法连接Redis");
            return;
        }

        try {
            // 执行FT._LIST命令查看所有索引
            Object result = redisTemplate.execute((RedisCallback<Object>) connection -> {
                return connection.execute("FT._LIST");
            });

            System.out.println("索引列表: " + result);

            // 检查法律知识库索引
            checkIndex("idx:law_knowledge");

            // 检查相似问题库索引
            checkIndex("idx:similar_question");

        } catch (Exception e) {
            System.out.println("检查索引失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== 索引检查完成 ===");
    }

    private void checkIndex(String indexName) {
        try {
            Object result = redisTemplate.execute((RedisCallback<Object>) connection -> {
                return connection.execute("FT.INFO", indexName.getBytes());
            });

            if (result != null) {
                System.out.println("索引 " + indexName + " 存在");
            } else {
                System.out.println("索引 " + indexName + " 不存在");
            }
        } catch (Exception e) {
            System.out.println("检查索引 " + indexName + " 失败: " + e.getMessage());
        }
    }
}
