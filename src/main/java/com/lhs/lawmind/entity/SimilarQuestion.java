package com.lhs.lawmind.entity;

import java.util.Date;

/**
 * 相似问题库实体类
 * 对应Redis中存储的相似问题数据
 */
public class SimilarQuestion {

    private Long id;

    /**
     * 用户原问题
     */
    private String question;

    /**
     * 最终回答
     */
    private String answer;

    /**
     * 关联的知识点ID列表，逗号分隔
     */
    private String knowledgeIds;

    /**
     * 问题向量（JSON格式存储）
     */
    private String vector;

    /**
     * 访问次数
     */
    private Integer visitCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 最后访问时间
     */
    private Date lastVisitTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getKnowledgeIds() {
        return knowledgeIds;
    }

    public void setKnowledgeIds(String knowledgeIds) {
        this.knowledgeIds = knowledgeIds;
    }

    public String getVector() {
        return vector;
    }

    public void setVector(String vector) {
        this.vector = vector;
    }

    public Integer getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getLastVisitTime() {
        return lastVisitTime;
    }

    public void setLastVisitTime(Date lastVisitTime) {
        this.lastVisitTime = lastVisitTime;
    }
}
