package com.oj.agent.core.conversation.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.core.conversation.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 基于会话标识加行锁，串行化同会话消息序号分配。
     */
    @Select("""
            SELECT id
            FROM conversation
            WHERE conversation_id = #{conversationId}
            FOR UPDATE
            """)
    Long lockByConversationId(@Param("conversationId") String conversationId);

    /**
     * 根据会话标识查询会话。
     */
    @Select("""
            SELECT id,
                   user_id,
                   conversation_id,
                   title,
                   current_question_id,
                   session_status,
                   last_message_time,
                   is_delete,
                   create_time,
                   update_time
            FROM conversation
            WHERE conversation_id = #{conversationId}
              AND is_delete = 0
            LIMIT 1
            """)
    Conversation selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 根据会话标识更新最后消息时间。
     */
    @Update("""
            UPDATE conversation
            SET last_message_time = #{lastMessageTime}
            WHERE conversation_id = #{conversationId}
              AND is_delete = 0
            """)
    int updateLastMessageTimeByConversationId(@Param("conversationId") String conversationId,
                                              @Param("lastMessageTime") LocalDateTime lastMessageTime);

    /**
     * 仅在请求仍对应最新用户消息时原子更新当前题目。
     */
    @Update("""
            UPDATE conversation
            SET current_question_id = #{currentQuestionId}
            WHERE conversation_id = #{conversationId}
              AND is_delete = 0
              AND #{requestSequenceNo} = (
                    SELECT latest_user_message.sequence_no
                    FROM (
                        SELECT cm.sequence_no
                        FROM conversation_message cm
                        WHERE cm.conversation_id = #{conversationId}
                          AND cm.sender = 'USER'
                        ORDER BY cm.sequence_no DESC
                        LIMIT 1
                    ) latest_user_message
              )
            """)
    int updateCurrentQuestionIdIfLatestUserMessageMatches(@Param("conversationId") String conversationId,
                                                          @Param("requestSequenceNo") Integer requestSequenceNo,
                                                          @Param("currentQuestionId") Long currentQuestionId);

    /**
     * 仅在标题仍为默认值且请求仍对应最新用户消息时回填会话标题。
     */
    @Update("""
            UPDATE conversation
            SET title = #{title}
            WHERE conversation_id = #{conversationId}
              AND is_delete = 0
              AND (title IS NULL OR TRIM(title) = '' OR title = '新会话')
              AND #{requestSequenceNo} = (
                    SELECT latest_user_message.sequence_no
                    FROM (
                        SELECT cm.sequence_no
                        FROM conversation_message cm
                        WHERE cm.conversation_id = #{conversationId}
                          AND cm.sender = 'USER'
                        ORDER BY cm.sequence_no DESC
                        LIMIT 1
                    ) latest_user_message
              )
            """)
    int updateTitleIfLatestUserMessageMatchesAndTitleDefault(@Param("conversationId") String conversationId,
                                                             @Param("requestSequenceNo") Integer requestSequenceNo,
                                                             @Param("title") String title);

    /**
     * 分页查询当前用户未删除会话。
     */
    @Select("""
            SELECT id,
                   user_id,
                   conversation_id,
                   title,
                   current_question_id,
                   session_status,
                   last_message_time,
                   is_delete,
                   create_time,
                   update_time
            FROM conversation
            WHERE user_id = #{userId}
              AND is_delete = 0
            ORDER BY last_message_time DESC, id DESC
            """)
    IPage<Conversation> selectPageByUserId(Page<Conversation> page,
                                           @Param("userId") Long userId);

    /**
     * 软删除当前用户自己的会话。
     */
    @Update("""
            UPDATE conversation
            SET is_delete = 1,
                update_time = CURRENT_TIMESTAMP
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND is_delete = 0
            """)
    int softDeleteByConversationId(@Param("conversationId") String conversationId,
                                   @Param("userId") Long userId);
}
