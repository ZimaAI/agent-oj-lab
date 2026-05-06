package com.oj.agent.core.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.rag.model.command.KnowledgeSegmentRagasTaskCreateCommand;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentRagasTask;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskDetailQuery;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskListQuery;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskPageQuery;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskDetailResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskPageResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskResult;

import java.util.List;

public interface KnowledgeSegmentRagasTaskService extends IService<KnowledgeSegmentRagasTask> {

    KnowledgeSegmentRagasTaskResult createTask(KnowledgeSegmentRagasTaskCreateCommand command);

    List<KnowledgeSegmentRagasTaskResult> listTasks(KnowledgeSegmentRagasTaskListQuery query);

    KnowledgeSegmentRagasTaskPageResult pageTasks(KnowledgeSegmentRagasTaskPageQuery query);

    KnowledgeSegmentRagasTaskDetailResult getTaskDetail(KnowledgeSegmentRagasTaskDetailQuery query);
}
