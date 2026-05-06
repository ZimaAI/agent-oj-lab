package com.oj.agent.core.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionTag;
import com.oj.agent.core.question.model.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlgorithmQuestionTagMapper extends BaseMapper<AlgorithmQuestionTag> {

    /**
     * 查询单个题目的标签列表。
     */
    @Select("""
            SELECT t.id,
                   t.tag_name,
                   t.tag_type,
                   t.description,
                   t.create_time,
                   t.update_time
            FROM algorithm_question_tag aqt
            INNER JOIN tag t ON t.id = aqt.tag_id
            WHERE aqt.question_id = #{questionId}
              AND t.is_delete = 0
            ORDER BY aqt.id ASC
            """)
    List<Tag> selectTagsByQuestionId(@Param("questionId") Long questionId);

    /**
     * 批量查询题目标签关联结果。
     */
    @Select("""
            <script>
            SELECT aqt.question_id AS questionId,
                   t.id AS id,
                   t.tag_name AS tagName,
                   t.tag_type AS tagType,
                   t.description AS description,
                   t.create_time AS createTime,
                   t.update_time AS updateTime
            FROM algorithm_question_tag aqt
            INNER JOIN tag t ON t.id = aqt.tag_id
            WHERE aqt.question_id IN
            <foreach collection='questionIds' item='questionId' open='(' separator=',' close=')'>
              #{questionId}
            </foreach>
              AND t.is_delete = 0
            ORDER BY aqt.id ASC
            </script>
            """)
    List<Map<String, Object>> selectQuestionTagPairsByQuestionIds(@Param("questionIds") List<Long> questionIds);

    /**
     * 查询命中全部标签的题目 id。
     */
    @Select("""
            <script>
            SELECT question_id
            FROM algorithm_question_tag
            WHERE tag_id IN
            <foreach collection='tagIds' item='tagId' open='(' separator=',' close=')'>
              #{tagId}
            </foreach>
            GROUP BY question_id
            HAVING COUNT(DISTINCT tag_id) = #{tagCount}
            </script>
            """)
    List<Long> selectQuestionIdsByTagIds(@Param("tagIds") List<Long> tagIds, @Param("tagCount") long tagCount);
}
