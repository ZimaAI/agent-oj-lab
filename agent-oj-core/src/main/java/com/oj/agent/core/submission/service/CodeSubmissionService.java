package com.oj.agent.core.submission.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.submission.model.command.CodeRunCommand;
import com.oj.agent.core.submission.model.command.CodeSubmissionCreateCommand;
import com.oj.agent.core.submission.model.command.CodeSubmissionUpdateCommand;
import com.oj.agent.core.submission.model.query.CodeSubmissionGetQuery;
import com.oj.agent.core.submission.model.query.CodeSubmissionPageQuery;
import com.oj.agent.core.submission.model.result.CodeExecutionResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;

public interface CodeSubmissionService {

    CodeSubmissionResult createPendingSubmission(CodeSubmissionCreateCommand command);

    boolean updateSubmission(CodeSubmissionUpdateCommand command);

    CodeExecutionResult executeCode(CodeRunCommand command);

    Page<CodeSubmissionResult> pageSubmissions(CodeSubmissionPageQuery query);

    CodeSubmissionResult getSubmission(CodeSubmissionGetQuery query);
}
