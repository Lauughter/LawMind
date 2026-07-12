package com.lhs.lawmind.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Golden Dataset 中的单条测试用例
 */
public class GoldenDatasetRecord {

    private String id;
    private String question;
    private String intent;

    @JsonProperty("expected_law_type")
    private String expectedLawType;

    private String difficulty;

    @JsonProperty("key_points")
    private List<String> keyPoints;

    @JsonProperty("expected_answer_contains")
    private List<String> expectedAnswerContains;

    @JsonProperty("source_requirement")
    private String sourceRequirement;

    @JsonProperty("forbidden_content")
    private String forbiddenContent;

    @JsonProperty("min_retrieval_count")
    private Integer minRetrievalCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getExpectedLawType() { return expectedLawType; }
    public void setExpectedLawType(String expectedLawType) { this.expectedLawType = expectedLawType; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public List<String> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }

    public List<String> getExpectedAnswerContains() { return expectedAnswerContains; }
    public void setExpectedAnswerContains(List<String> expectedAnswerContains) { this.expectedAnswerContains = expectedAnswerContains; }

    public String getSourceRequirement() { return sourceRequirement; }
    public void setSourceRequirement(String sourceRequirement) { this.sourceRequirement = sourceRequirement; }

    public String getForbiddenContent() { return forbiddenContent; }
    public void setForbiddenContent(String forbiddenContent) { this.forbiddenContent = forbiddenContent; }

    public Integer getMinRetrievalCount() { return minRetrievalCount; }
    public void setMinRetrievalCount(Integer minRetrievalCount) { this.minRetrievalCount = minRetrievalCount; }
}
