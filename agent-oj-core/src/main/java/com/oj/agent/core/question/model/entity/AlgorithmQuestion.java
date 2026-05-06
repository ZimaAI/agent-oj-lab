package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("algorithm_question")
public class AlgorithmQuestion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String difficulty;

    private String type;

    private String sharedFunctionName;

    private String generateModelKey;

    private String questionDigest;

    private String sharedCodeSkeleton;

    @TableField("shared_test_cases")
    private String standardCasePool;

    private String conversationId;

    private String traceId;

    private String agentName;

    private String vectorSyncStatus;

    private String vectorSyncErrorMessage;

    @TableField(exist = false)
    private List<String> tags;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // Compatibility accessors for in-flight refactors.
    @Deprecated
    public String getLanguage() {
        return null;
    }

    @Deprecated
    public void setLanguage(String language) {
        // no-op: question-level language is removed
    }

    @Deprecated
    public String getFunctionName() {
        return sharedFunctionName;
    }

    @Deprecated
    public void setFunctionName(String functionName) {
        this.sharedFunctionName = functionName;
    }

    @Deprecated
    public String getCodeSkeleton() {
        return sharedCodeSkeleton;
    }

    @Deprecated
    public void setCodeSkeleton(String codeSkeleton) {
        this.sharedCodeSkeleton = codeSkeleton;
    }

    @Deprecated
    public String getReferenceAnswer() {
        return null;
    }

    @Deprecated
    public void setReferenceAnswer(String referenceAnswer) {
        // no-op: moved to algorithm_code
    }

    @Deprecated
    public String getTestCases() {
        return standardCasePool;
    }

    @Deprecated
    public void setTestCases(String testCases) {
        this.standardCasePool = testCases;
    }

    @Deprecated
    public String getSharedTestCases() {
        return standardCasePool;
    }

    @Deprecated
    public void setSharedTestCases(String sharedTestCases) {
        this.standardCasePool = sharedTestCases;
    }
}
