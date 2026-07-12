package com.lhs.lawmind.utils;

import com.lhs.lawmind.entity.LawKnowledge;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON工具类
 * 提供JSON构建和解析方法
 */
@Slf4j
public class JsonUtil {

    /**
     * 构建knowledge_match JSON字符串
     *
     * @param relatedKnowledge 法律知识列表
     * @return JSON字符串
     */
    public static String buildKnowledgeMatchJson(List<LawKnowledge> relatedKnowledge) {
        if (relatedKnowledge == null || relatedKnowledge.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < relatedKnowledge.size(); i++) {
            LawKnowledge knowledge = relatedKnowledge.get(i);
            json.append("{");
            // 安全处理ID字段
            json.append("\"knowledge_id\":\"").append(knowledge.getId() != null ? knowledge.getId() : "").append("\",");
            // 安全处理标题字段
            json.append("\"title\":\"").append(escapeJson(knowledge.getTitle())).append("\",");
            // 安全处理内容字段
            json.append("\"content\":\"").append(escapeJson(knowledge.getContent())).append("\",");
            // 安全处理法律类型字段
            json.append("\"law_type\":\"").append(escapeJson(knowledge.getLawType())).append("\",");
            // 安全处理相似度score字段，保持高精度
            json.append("\"score\":")
                .append(knowledge.getScore() != null ? knowledge.getScore() : 0.0);
            json.append("}");
            if (i < relatedKnowledge.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    /**
     * 从JSON字符串解析法律知识列表
     *
     * @param json JSON字符串
     * @return 法律知识列表
     */
    public static List<LawKnowledge> parseLegalKnowledgeFromJson(String json) {
        List<LawKnowledge> knowledgeList = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            log.error("JSON字符串为空: {}", json);
            return knowledgeList;
        }

        try {
            // 简单的JSON解析，处理数组格式
            json = json.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) {
                log.error("JSON字符串格式错误: {}", json);
                return knowledgeList;
            }

            // 去除数组括号
            json = json.substring(1, json.length() - 1);
            if (json.isEmpty()) {
                return knowledgeList;
            }

            // 使用更智能的方式分割JSON对象
            List<String> objects = splitJsonObjects(json);
            log.info("分割后的JSON对象数量: {}", objects.size());
            
            for (String objStr : objects) {
                // 解析单个法律知识对象
                LawKnowledge knowledge = parseLegalKnowledgeObject(objStr);
                if (knowledge != null) {
                    knowledgeList.add(knowledge);
                    log.info("成功解析法律知识: lawType={}, title={}", 
                        knowledge.getLawType(), knowledge.getTitle());
                } else {
                    log.warn("解析单个法律知识对象失败: {}", objStr);
                }
            }
        } catch (Exception e) {
            // 解析失败时返回空列表
            log.error("解析JSON失败: {}", e.getMessage(), e);
        }

        log.info("解析完成，共解析到 {} 条法律知识", knowledgeList.size());
        return knowledgeList;
    }
    
    /**
     * 智能分割JSON对象数组
     * 处理content字段中可能包含的逗号和大括号
     */
    private static List<String> splitJsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        StringBuilder currentObj = new StringBuilder();
        int braceCount = 0;
        boolean inString = false;
        boolean escape = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escape) {
                currentObj.append(c);
                escape = false;
                continue;
            }
            
            if (c == '\\') {
                currentObj.append(c);
                escape = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                currentObj.append(c);
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    braceCount++;
                    if (braceCount == 1) {
                        // 开始一个新的对象
                        currentObj = new StringBuilder();
                    }
                    currentObj.append(c);
                } else if (c == '}') {
                    braceCount--;
                    currentObj.append(c);
                    if (braceCount == 0) {
                        // 完成一个对象
                        objects.add(currentObj.toString());
                        currentObj = new StringBuilder();
                    }
                } else if (c == ',' && braceCount == 0) {
                    // 跳过对象之间的逗号
                    continue;
                } else {
                    currentObj.append(c);
                }
            } else {
                currentObj.append(c);
            }
        }
        
        return objects;
    }

    /**
     * 解析单个法律知识JSON对象
     *
     * @param jsonObject JSON对象字符串
     * @return 法律知识对象
     */
    private static LawKnowledge parseLegalKnowledgeObject(String jsonObject) {
        try {
            LawKnowledge knowledge = new LawKnowledge();

            // 提取lawType（支持不同大小写）
            String lawType = extractValue(jsonObject, "lawType");
            if (lawType == null) {
                lawType = extractValue(jsonObject, "LawType");
            }
            if (lawType != null) {
                knowledge.setLawType(lawType);
            }

            // 提取title
            String title = extractValue(jsonObject, "title");
            if (title == null) {
                title = extractValue(jsonObject, "Title");
            }
            if (title != null) {
                knowledge.setTitle(title);
            }

            // 提取content
            String content = extractValue(jsonObject, "content");
            if (content == null) {
                content = extractValue(jsonObject, "Content");
            }
            if (content != null) {
                knowledge.setContent(content);
            }

            // 至少需要有内容才返回
            if (knowledge.getContent() != null && !knowledge.getContent().isEmpty()) {
                return knowledge;
            }
        } catch (Exception e) {
            // 解析失败时返回null
            log.error("解析法律知识对象失败: error={}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 从JSON对象字符串中提取指定字段的值
     * 支持转义的引号
     *
     * @param jsonObject JSON对象字符串
     * @param key 字段名
     * @return 字段值
     */
    private static String extractValue(String jsonObject, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int startIndex = jsonObject.indexOf(pattern);
            if (startIndex == -1) {
                return null;
            }
            startIndex += pattern.length();
            
            // 跳过可能的空格
            while (startIndex < jsonObject.length() && 
                   (jsonObject.charAt(startIndex) == ' ' || jsonObject.charAt(startIndex) == '\t')) {
                startIndex++;
            }
            
            // 检查是否是字符串（以"开头）
            if (startIndex >= jsonObject.length() || jsonObject.charAt(startIndex) != '"') {
                return null;
            }
            startIndex++; // 跳过开头的"
            
            StringBuilder value = new StringBuilder();
            boolean escape = false;
            
            for (int i = startIndex; i < jsonObject.length(); i++) {
                char c = jsonObject.charAt(i);
                
                if (escape) {
                    // 处理转义字符
                    switch (c) {
                        case '"':
                        case '\\':
                        case '/':
                            value.append(c);
                            break;
                        case 'b':
                            value.append('\b');
                            break;
                        case 'f':
                            value.append('\f');
                            break;
                        case 'n':
                            value.append('\n');
                            break;
                        case 'r':
                            value.append('\r');
                            break;
                        case 't':
                            value.append('\t');
                            break;
                        default:
                            value.append(c);
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    // 遇到未转义的引号，结束提取
                    return value.toString();
                } else {
                    value.append(c);
                }
            }
            
            // 如果没有找到结束的引号，返回null
            return null;
        } catch (Exception e) {
            log.error("提取字段值失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * JSON字符串转义
     *
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    public static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}