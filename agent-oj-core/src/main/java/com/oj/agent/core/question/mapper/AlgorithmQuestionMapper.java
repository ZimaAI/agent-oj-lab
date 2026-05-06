package com.oj.agent.core.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlgorithmQuestionMapper extends BaseMapper<AlgorithmQuestion> {

    /**
     * 按筛选条件分页查询算法题。
     */
    @Select("""
            <script>
            SELECT id,
                   user_id,
                   title,
                   description,
                   difficulty,
                   type,
                   shared_function_name,
                   generate_model_key,
                   question_digest,
                   shared_code_skeleton,
                   conversation_id,
                   trace_id,
                   agent_name,
                   vector_sync_status,
                   vector_sync_error_message,
                   is_delete,
                   create_time,
                   update_time
            FROM algorithm_question
            WHERE is_delete = 0
            <if test='difficulty != null and difficulty != ""'>
              AND difficulty = #{difficulty}
            </if>
            <if test='type != null and type != ""'>
              AND type = #{type}
            </if>
            <if test='keyword != null and keyword != ""'>
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                OR description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test='questionIds != null and questionIds.size() > 0'>
              AND id IN
              <foreach collection='questionIds' item='questionId' open='(' separator=',' close=')'>
                #{questionId}
              </foreach>
            </if>
            ORDER BY id DESC
            </script>
            """)
    IPage<AlgorithmQuestion> selectPageByCondition(Page<AlgorithmQuestion> page,
                                                   @Param("difficulty") String difficulty,
                                                   @Param("type") String type,
                                                   @Param("keyword") String keyword,
                                                   @Param("questionIds") List<Long> questionIds);

    /**
     * 按筛选条件统计算法题总量。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM algorithm_question
            WHERE is_delete = 0
            <if test='difficulty != null and difficulty != ""'>
              AND difficulty = #{difficulty}
            </if>
            <if test='type != null and type != ""'>
              AND type = #{type}
            </if>
            <if test='keyword != null and keyword != ""'>
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                OR description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test='questionIds != null and questionIds.size() > 0'>
              AND id IN
              <foreach collection='questionIds' item='questionId' open='(' separator=',' close=')'>
                #{questionId}
              </foreach>
            </if>
            </script>
            """)
    long countByCondition(@Param("difficulty") String difficulty,
                          @Param("type") String type,
                          @Param("keyword") String keyword,
                          @Param("questionIds") List<Long> questionIds);
}
