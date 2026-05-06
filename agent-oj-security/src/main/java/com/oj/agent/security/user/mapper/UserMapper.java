package com.oj.agent.security.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.security.user.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT DISTINCT p.permission_code " +
            "FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.is_delete = 0")
    List<String> selectUserPermissions(Long userId);
}
