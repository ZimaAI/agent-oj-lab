package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.oj.agent.core.workflow.enums.UserIntentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;




@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentRecognitionOutputDTO {

    // 意图识别结果
    @JsonProperty("intent")
    @JsonPropertyDescription("意图识别结果")
    private UserIntentType intent;

}
