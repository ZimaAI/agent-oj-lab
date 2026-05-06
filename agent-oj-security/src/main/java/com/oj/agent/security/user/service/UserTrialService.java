package com.oj.agent.security.user.service;

import com.oj.agent.security.user.model.command.UserTrialCountDecrementCommand;

public interface UserTrialService {

    void decrementTrialCount(UserTrialCountDecrementCommand command);
}
