package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.AiChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiChatMapper {
    List<AiChat> selectAll();
    List<AiChat> selectByUserId(Long userId);
    List<AiChat> selectByConversationId(Long conversationId);
    List<AiChat> selectByConversationIdPage(@Param("conversationId") Long conversationId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countByConversationId(@Param("conversationId") Long conversationId);
    AiChat selectById(Long id);
    int insert(AiChat aiChat);
    int update(AiChat aiChat);
    int delete(Long id);
    List<AiChat> selectHighFrequencyUnmatched(@Param("limit") int limit);
    List<AiChat> selectRecentByConversationId(@Param("conversationId") Long conversationId, @Param("limit") int limit);
    int updateFeedback(@Param("id") Long id, @Param("feedback") Integer feedback);
    List<AiChat> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);
    long countAll();
    List<AiChat> selectByUserIdPage(@Param("userId") Long userId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countByUserId(@Param("userId") Long userId);
    List<AiChat> selectPendingReviewPage(@Param("offset") int offset, @Param("pageSize") int pageSize);
    long countPendingReview();
    int updateReview(@Param("id") Long id, @Param("feedbackStatus") String feedbackStatus,
                     @Param("reviewedBy") Long reviewedBy, @Param("reviewNotes") String reviewNotes);
    int updateFeedbackContent(@Param("id") Long id, @Param("feedbackContent") String feedbackContent);
}