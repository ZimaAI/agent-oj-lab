package com.oj.agent.core.trace.service;

import com.oj.agent.core.trace.model.command.TraceCompleteCommand;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.command.TraceStartCommand;

public interface WorkflowTraceService {

    void startTrace(TraceStartCommand command);

    void completeTrace(TraceCompleteCommand command);

    Long startTraceItem(TraceItemStartCommand command);

    void finishTraceItem(TraceItemFinishCommand command);
}
