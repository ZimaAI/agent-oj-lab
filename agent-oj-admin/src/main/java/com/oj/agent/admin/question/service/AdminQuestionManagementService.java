package com.oj.agent.admin.question.service;

import com.oj.agent.admin.question.model.command.AdminQuestionBatchDeleteCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionCreateCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionDocumentDeleteCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionDocumentUploadCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionUpdateCommand;
import com.oj.agent.admin.question.model.query.AdminKnowledgeSegmentPageQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDocumentChildSegmentQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDocumentListQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDocumentSegmentPageQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDualDetailQuery;
import com.oj.agent.admin.question.model.result.AdminBatchOperationResult;
import com.oj.agent.admin.question.model.result.AdminKnowledgeSegmentPageResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentSegmentPageResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentSegmentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDualDetailResult;

import java.util.List;


public interface AdminQuestionManagementService {

    /**
     * Query dual-detail data for one question.
     */
    AdminQuestionDualDetailResult getQuestionDualDetail(AdminQuestionDualDetailQuery query);

    /**
     * Create one question and return dual-detail data.
     */
    AdminQuestionDualDetailResult createQuestion(AdminQuestionCreateCommand command);

    /**
     * Upload one document for question and bind relation.
     */
    AdminQuestionDocumentResult uploadQuestionDocument(AdminQuestionDocumentUploadCommand command);

    /**
     * List documents bound to one question.
     */
    List<AdminQuestionDocumentResult> listQuestionDocuments(AdminQuestionDocumentListQuery query);

    /**
     * Delete one bound document and related knowledge data from one question.
     */
    void deleteQuestionDocument(AdminQuestionDocumentDeleteCommand command);

    /**
     * Page top-level segments for one question document.
     */
    AdminQuestionDocumentSegmentPageResult pageQuestionDocumentSegments(AdminQuestionDocumentSegmentPageQuery query);

    /**
     * List child segments under one parent segment.
     */
    List<AdminQuestionDocumentSegmentResult> listQuestionDocumentChildSegments(AdminQuestionDocumentChildSegmentQuery query);

    /**
     * 全量分页查询知识片段。
     */
    AdminKnowledgeSegmentPageResult pageKnowledgeSegments(AdminKnowledgeSegmentPageQuery query);

    /**
     * Update one question and sync vector projection.
     */
    AdminQuestionDualDetailResult updateQuestion(AdminQuestionUpdateCommand command);

    /**
     * Batch delete questions and return per-item results.
     */
    AdminBatchOperationResult batchDeleteQuestions(AdminQuestionBatchDeleteCommand command);
}
