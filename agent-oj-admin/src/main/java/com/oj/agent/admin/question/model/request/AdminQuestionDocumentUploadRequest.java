package com.oj.agent.admin.question.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDocumentUploadRequest {

    private Long questionId;

    private MultipartFile file;
}
