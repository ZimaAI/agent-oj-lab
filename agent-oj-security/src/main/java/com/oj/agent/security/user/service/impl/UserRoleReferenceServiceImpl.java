package com.oj.agent.security.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.security.user.mapper.UserRoleMapper;
import com.oj.agent.security.user.model.entity.UserRole;
import com.oj.agent.security.user.model.query.UserRoleUsageQuery;
import com.oj.agent.security.user.model.result.UserRoleUsageResult;
import com.oj.agent.security.user.service.UserRoleReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRoleReferenceServiceImpl implements UserRoleReferenceService {

    private final UserRoleMapper userRoleMapper;

    @Override
    public UserRoleUsageResult getRoleUsage(UserRoleUsageQuery query) {
        UserRoleUsageResult result = new UserRoleUsageResult();
        Long roleId = query == null ? null : query.getRoleId();
        result.setRoleId(roleId);
        if (roleId == null) {
            result.setUserCount(0L);
            return result;
        }
        Long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, roleId));
        result.setUserCount(userCount == null ? 0L : userCount);
        return result;
    }
}
