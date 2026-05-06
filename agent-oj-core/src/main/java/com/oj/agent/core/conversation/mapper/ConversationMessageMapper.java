package com.oj.agent.core.conversation.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.conversation.model.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0)
            FROM conversation_message
            WHERE conversation_id = #{conversationId}
            """)
    Integer selectMaxSequenceNo(@Param("conversationId") String conversationId);

    /**
     * 按会话分页查询消息明细。
     */
    @Select("""
            SELECT id,
                   conversation_id,
                   sender,
                   message_type,
                   content,
                   question_id,
                   submission_id,
                   evaluation_id,
                   trace_id,
                   sequence_no,
                   result_type,
                   result_data,
                   create_time,
                   update_time
            FROM conversation_message
            WHERE conversation_id = #{conversationId}
            ORDER BY sequence_no ASC
            """)
    IPage<ConversationMessage> selectPageByConversationId(Page<ConversationMessage> page,
                                                          @Param("conversationId") String conversationId);

    /**
     * 查询会话最后一条消息内容。
     */
    @Select("""
            SELECT content
            FROM conversation_message
            WHERE conversation_id = #{conversationId}
            ORDER BY sequence_no DESC
            LIMIT 1
            """)
    String selectLatestContentByConversationId(@Param("conversationId") String conversationId);
}
