package com.oj.agent.core.workflow.converter;

import com.oj.agent.core.workflow.model.command.WorkflowStartCommand;
import com.oj.agent.core.workflow.model.dto.AnswerRAGSegmentDTO;
import com.oj.agent.core.workflow.model.request.CodeEvaluationStreamRequest;
import com.oj.agent.core.workflow.model.request.UserMessageRequest;
import com.oj.agent.core.workflow.model.response.GraphNodeResponse;
import com.oj.agent.core.workflow.model.result.AnswerRagSegmentResult;
import com.oj.agent.core.workflow.model.result.WorkflowStreamEventResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentResult;

import java.util.List;

public final class WorkflowConverter {

    private WorkflowConverter() {
    }

    public static WorkflowStartCommand toStartCommand(UserMessageRequest request) {
        WorkflowStartCommand command = new WorkflowStartCommand();
        command.setCodeEvaluation(false);
        if (request != null) {
            command.setConversationId(request.getConversationId());
            command.setMessage(request.getMessage());
        }
        return command;
    }

    public static WorkflowStartCommand toCodeEvaluationStartCommand(CodeEvaluationStreamRequest request) {
        WorkflowStartCommand command = new WorkflowStartCommand();
        command.setCodeEvaluation(true);
        if (request != null) {
            command.setConversationId(request.getConversationId());
            command.setMessage(request.getMessage());
            command.setCode(request.getCode());
            command.setLanguage(request.getLanguage());
        }
        return command;
    }

    public static AnswerRagSegmentResult toAnswerRagSegmentResult(AnswerRAGSegmentDTO dto) {
        if (dto == null) {
            return null;
        }
        return new AnswerRagSegmentResult(
                dto.getId(),
                dto.getText(),
                dto.getChunkId(),
                dto.getRawSimilarity(),
                dto.getSimilarityScore(),
                dto.getRrfScore(),
                dto.getFinalScore()
        );
    }

    public static List<AnswerRagSegmentResult> toAnswerRagSegmentResults(List<AnswerRAGSegmentDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        return dtos.stream()
                .map(WorkflowConverter::toAnswerRagSegmentResult)
                .toList();
    }

    public static GraphNodeResponse toGraphNodeResponse(WorkflowStreamEventResult result) {
        if (result == null) {
            return null;
        }
        return GraphNodeResponse.builder()
                .conversationId(result.getConversationId())
                .nodeName(result.getNodeName())
                .textType(result.getTextType())
                .text(result.getText())
                .error(result.isError())
                .complete(result.isComplete())
                .build();
    }

    public static AnswerRAGSegmentDTO toAnswerRagSegmentDto(KnowledgeSegmentResult segment,
                                                            Double rawSimilarity,
                                                            Double similarityScore,
                                                            Double rrfScore,
                                                            Double finalScore) {
        AnswerRAGSegmentDTO dto = new AnswerRAGSegmentDTO();
        if (segment != null) {
            dto.setId(segment.getId());
            dto.setText(segment.getText());
            dto.setChunkId(segment.getChunkId());
            dto.setDocumentId(segment.getDocumentId());
            dto.setChunkOrder(segment.getChunkOrder());
        }
        dto.setRawSimilarity(rawSimilarity);
        dto.setSimilarityScore(similarityScore);
        dto.setRrfScore(rrfScore);
        dto.setFinalScore(finalScore);
        return dto;
    }
}
