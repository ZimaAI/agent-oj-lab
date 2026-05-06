package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class LanguageCodeOutputDTO {

    @JsonPropertyDescription("代码生成节点是否成功")
    private boolean success;

    @JsonPropertyDescription("目标编程语言名称，例如 java、python、javascript")
    private String language;

    @JsonPropertyDescription("函数或方法名，需与执行器调用入口完全一致")
    private String functionName;

    @JsonPropertyDescription("提供给用户填写的代码框架，保留签名与占位逻辑，不应是完整答案")
    private String codeSkeleton;

    @JsonPropertyDescription("用于判题执行的参考答案代码，必须可直接运行并返回正确结果")
    private String referenceAnswer;

    @JsonPropertyDescription("失败时的错误信息，成功时可为空")
    private String errorMessage;
}
