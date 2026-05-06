package com.oj.agent.core.submission.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.submission.model.entity.CodeSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface CodeSubmissionMapper extends BaseMapper<CodeSubmission> {

    /**
     * 分页查询当前用户的提交记录。
     */
    @Select("""
            <script>
            SELECT cs.id,
                   cs.algorithm_question_id,
                   aq.title AS questionTitle,
                   cs.pass_count,
                   cs.total_count,
                   cs.language,
                   cs.create_time
            FROM code_submission cs
            LEFT JOIN algorithm_question aq ON aq.id = cs.algorithm_question_id
            WHERE cs.user_id = #{userId}
              AND cs.is_delete = 0
            <if test='algorithmQuestionId != null'>
              AND cs.algorithm_question_id = #{algorithmQuestionId}
            </if>
            ORDER BY cs.create_time DESC, cs.id DESC
            </script>
            """)
    IPage<Map<String, Object>> selectSubmissionPage(Page<CodeSubmission> page,
                                                    @Param("userId") Long userId,
                                                    @Param("algorithmQuestionId") Long algorithmQuestionId);

    /**
     * 查询当前用户的提交详情。
     */
    @Select("""
            SELECT cs.id,
                   cs.user_id,
                   cs.algorithm_question_id,
                   aq.title AS questionTitle,
                   cs.code,
                   cs.language,
                   cs.test_results,
                   cs.pass_count,
                   cs.total_count,
                   cs.create_time,
                   ce.summary AS codeEvaluation
            FROM code_submission cs
            LEFT JOIN algorithm_question aq ON aq.id = cs.algorithm_question_id
            LEFT JOIN code_evaluation ce ON ce.id = (
                SELECT latest_ce.id
                FROM code_evaluation latest_ce
                WHERE latest_ce.submission_id = cs.id
                  AND latest_ce.is_delete = 0
                ORDER BY latest_ce.create_time DESC, latest_ce.id DESC
                LIMIT 1
            )
            WHERE cs.id = #{id}
              AND cs.user_id = #{userId}
              AND cs.is_delete = 0
            LIMIT 1
            """)
    Map<String, Object> selectSubmissionDetail(@Param("id") Long id,
                                               @Param("userId") Long userId);
}
