package com.oj.agent.core.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.agent.core.trace.model.entity.TraceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TraceItemMapper extends BaseMapper<TraceItem> {

    /**
     * 按链路 id 查询节点记录列表。
     */
    @Select("""
            SELECT id,
                   trace_id,
                   node_name,
                   item_type,
                   item_key,
                   round_no,
                   tool_name,
                   input_payload,
                   output_payload,
                   input_summary,
                   output_summary,
                   prompt_tokens,
                   completion_tokens,
                   total_tokens,
                   status,
                   error_message,
                   start_timestamp,
                   end_timestamp,
                   create_time,
                   update_time
            FROM trace_item
            WHERE trace_id = #{traceId}
            ORDER BY id ASC
            """)
    List<TraceItem> selectByTraceId(@Param("traceId") String traceId);
}
