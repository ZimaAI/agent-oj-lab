package com.oj.agent.admin.question.ragas.service;

import com.oj.agent.admin.question.ragas.model.command.AdminRagasAnswerGenerateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasDeleteCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasEvaluateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasGenerateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasTaskCreateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasUpdateCommand;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasListQuery;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasTaskDetailQuery;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasTaskListQuery;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasTaskPageQuery;
import com.oj.agent.admin.question.ragas.model.result.AdminQuestionDocumentSegmentRagasResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskDetailResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskPageResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskResult;

import java.util.List;

public interface AdminQuestionRagasService {

    List<AdminQuestionDocumentSegmentRagasResult> generateRagas(AdminRagasGenerateCommand command);

    List<AdminQuestionDocumentSegmentRagasResult> listRagas(AdminRagasListQuery query);

    List<AdminQuestionDocumentSegmentRagasResult> updateRagas(AdminRagasUpdateCommand command);

    void deleteRagas(AdminRagasDeleteCommand command);

    List<AdminQuestionDocumentSegmentRagasResult> generateAnswers(AdminRagasAnswerGenerateCommand command);

    List<AdminQuestionDocumentSegmentRagasResult> evaluateRagas(AdminRagasEvaluateCommand command);

    AdminRagasTaskResult createRagasTask(AdminRagasTaskCreateCommand command);

    List<AdminRagasTaskResult> listRagasTasks(AdminRagasTaskListQuery query);

    AdminRagasTaskPageResult pageRagasTasks(AdminRagasTaskPageQuery query);

    AdminRagasTaskDetailResult getRagasTaskDetail(AdminRagasTaskDetailQuery query);
}
