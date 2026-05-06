package com.oj.agent.core.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.trace.model.entity.Trace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TraceMapper extends BaseMapper<Trace> {

    /**
     * 按筛选条件分页查询链路。
     */
    @Select("""
            <script>
            SELECT id,
                   trace_id,
                   conversation_id,
                   user_id,
                   request_message,
                   current_algorithm_question_id,
                   current_algorithm_question_snapshot,
                   status,
                   error_message,
                   create_time,
                   update_time
            FROM trace
            WHERE 1 = 1
            <if test='traceId != null and traceId != ""'>
              AND trace_id = #{traceId}
            </if>
            <if test='conversationId != null and conversationId != ""'>
              AND conversation_id = #{conversationId}
            </if>
            <if test='userId != null'>
              AND user_id = #{userId}
            </if>
            <if test='requestMessage != null and requestMessage != ""'>
              AND request_message LIKE CONCAT('%', #{requestMessage}, '%')
            </if>
            <if test='status != null and status != ""'>
              AND status = #{status}
            </if>
            ORDER BY id DESC
            </script>
            """)
    IPage<Trace> selectPageByCondition(Page<Trace> page,
                                       @Param("traceId") String traceId,
                                       @Param("conversationId") String conversationId,
                                       @Param("userId") Long userId,
                                       @Param("requestMessage") String requestMessage,
                                       @Param("status") String status);

    /**
     * 按筛选条件统计链路总量。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM trace
            WHERE 1 = 1
            <if test='traceId != null and traceId != ""'>
              AND trace_id = #{traceId}
            </if>
            <if test='conversationId != null and conversationId != ""'>
              AND conversation_id = #{conversationId}
            </if>
            <if test='userId != null'>
              AND user_id = #{userId}
            </if>
            <if test='requestMessage != null and requestMessage != ""'>
              AND request_message LIKE CONCAT('%', #{requestMessage}, '%')
            </if>
            <if test='status != null and status != ""'>
              AND status = #{status}
            </if>
            </script>
            """)
    long countByCondition(@Param("traceId") String traceId,
                          @Param("conversationId") String conversationId,
                          @Param("userId") Long userId,
                          @Param("requestMessage") String requestMessage,
                          @Param("status") String status);
}
