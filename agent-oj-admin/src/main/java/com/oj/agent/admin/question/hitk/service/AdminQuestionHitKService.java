package com.oj.agent.admin.question.hitk.service;

import com.oj.agent.admin.question.hitk.model.command.AdminHitKQuestionGenerateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKQuestionUpdateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTaskBatchDeleteCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTaskRemarkUpdateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTestCreateCommand;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskDetailQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskPageQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskStatisticsQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTestListQuery;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKQuestionBatchResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskBatchDeleteResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskDetailResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskPageResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskStatisticsResult;

import java.util.List;

public interface AdminQuestionHitKService {

    AdminHitKQuestionBatchResult generateHitKQuestions(AdminHitKQuestionGenerateCommand command);

    AdminHitKQuestionBatchResult updateHitKQuestions(AdminHitKQuestionUpdateCommand command);

    AdminHitKTaskResult createHitKTest(AdminHitKTestCreateCommand command);

    List<AdminHitKTaskResult> listHitKTests(AdminHitKTestListQuery query);

    AdminHitKTaskDetailResult getHitKTestDetail(AdminHitKTaskDetailQuery query);

    AdminHitKTaskPageResult pageHitKTasks(AdminHitKTaskPageQuery query);

    AdminHitKTaskStatisticsResult statisticsHitKTasks(AdminHitKTaskStatisticsQuery query);

    AdminHitKTaskBatchDeleteResult batchDeleteHitKTasks(AdminHitKTaskBatchDeleteCommand command);

    AdminHitKTaskResult updateHitKTaskRemark(AdminHitKTaskRemarkUpdateCommand command);
}
