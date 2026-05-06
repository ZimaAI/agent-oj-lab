package com.oj.agent.core.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.core.question.model.entity.AlgorithmCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlgorithmCodeMapper extends BaseMapper<AlgorithmCode> {

    @Select("""
            SELECT id,
                   question_id,
                   language,
                   function_name,
                   code_skeleton,
                   reference_answer,
                   generate_model_key,
                   trace_id,
                   agent_name,
                   is_delete,
                   create_time,
                   update_time
            FROM algorithm_code
            WHERE question_id = #{questionId}
              AND is_delete = 0
            ORDER BY id ASC
            """)
    List<AlgorithmCode> selectByQuestionId(@Param("questionId") Long questionId);

    @Select("""
            SELECT id,
                   question_id,
                   language,
                   function_name,
                   code_skeleton,
                   reference_answer,
                   generate_model_key,
                   trace_id,
                   agent_name,
                   is_delete,
                   create_time,
                   update_time
            FROM algorithm_code
            WHERE question_id = #{questionId}
              AND UPPER(language) = UPPER(#{language})
              AND is_delete = 0
            LIMIT 1
            """)
    AlgorithmCode selectByQuestionIdAndLanguage(@Param("questionId") Long questionId,
                                                @Param("language") String language);
}
