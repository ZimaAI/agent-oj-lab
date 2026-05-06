package com.oj.agent.security.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.security.permission.model.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
