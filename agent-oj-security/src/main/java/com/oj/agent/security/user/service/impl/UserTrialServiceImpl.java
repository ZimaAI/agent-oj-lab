package com.oj.agent.security.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.oj.agent.security.user.mapper.UserMapper;
import com.oj.agent.security.user.model.command.UserTrialCountDecrementCommand;
import com.oj.agent.security.user.model.entity.User;
import com.oj.agent.security.user.service.UserTrialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserTrialServiceImpl implements UserTrialService {

    private final UserMapper userMapper;

    @Override
    public void decrementTrialCount(UserTrialCountDecrementCommand command) {
        if (command == null || command.getUserId() == null) {
            return;
        }

        userMapper.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, command.getUserId())
                .gt(User::getTrialCount, 0)
                .setSql("trial_count = trial_count - 1"));
    }
}
