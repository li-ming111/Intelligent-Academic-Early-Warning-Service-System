package com.academic.mapper;

import com.academic.entity.Feedback;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface FeedbackMapper {
    @Select("SELECT * FROM feedback WHERE teacher_id = #{teacherId} ORDER BY create_time DESC")
    List<Feedback> getFeedbacksByTeacherId(Long teacherId);
    
    @Select("SELECT * FROM feedback WHERE teacher_id = #{teacherId} AND category = #{category} ORDER BY create_time DESC")
    List<Feedback> getFeedbacksByTeacherIdAndCategory(Long teacherId, String category);
    
    /**
     * 回复反馈。
     *
     * <p>时间列是 {@code update_time}，**不是** {@code reply_time} ——
     * {@code feedback} 表从来没有 reply_time 这一列（全库 5 个 dump 里都查不到），
     * 早先写 {@code reply_time = NOW()} 会让这条 UPDATE 直接报
     * {@code Unknown column}，接口返回 500，教师端"回复反馈"点了没反应。
     */
    @Update("UPDATE feedback SET reply = #{reply}, status = 1, update_time = NOW() WHERE id = #{id}")
    int replyFeedback(Long id, String reply);
    
    @Select("SELECT * FROM feedback WHERE id = #{id}")
    Feedback getFeedbackById(Long id);
}