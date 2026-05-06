package com.oj.agent.core.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.question.model.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 分页查询标签列表。
     */
    @Select("""
            <script>
            SELECT id,
                   tag_name,
                   tag_type,
                   description,
                   create_time,
                   update_time
            FROM tag
            WHERE is_delete = 0
            <if test='keyword != null and keyword != ""'>
              AND tag_name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY id DESC
            </script>
            """)
    IPage<Tag> selectPageByKeyword(Page<Tag> page, @Param("keyword") String keyword);

    /**
     * 按标签名称批量查询有效标签。
     */
    @Select("""
            <script>
            SELECT id,
                   tag_name,
                   tag_type,
                   description,
                   is_delete,
                   create_time,
                   update_time
            FROM tag
            WHERE is_delete = 0
              AND tag_name IN
              <foreach collection='tagNames' item='tagName' open='(' separator=',' close=')'>
                #{tagName}
              </foreach>
            </script>
            """)
    List<Tag> selectByTagNames(@Param("tagNames") List<String> tagNames);
}
