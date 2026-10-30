package com.academic.mapper;

import com.academic.common.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 辅导员已发送的教师消息（type=2），带上接收教师姓名。
     */
    @Select("SELECT m.id, m.receiver_id AS receiverId, u.name AS receiverName, " +
            "m.title, m.content, m.type, m.status, m.created_at AS createdAt " +
            "FROM messages m " +
            "LEFT JOIN users u ON u.id = m.receiver_id " +
            "WHERE m.sender_id = #{senderId} AND m.type = 2 " +
            "ORDER BY m.created_at DESC LIMIT 50")
    List<Map<String, Object>> listSentTeacherMessages(@Param("senderId") Long senderId);

    /**
     * 教师回复（type=2，发送方 role=2，收件人是当前辅导员）。
     */
    @Select("SELECT m.id, m.sender_id AS senderId, u.name AS senderName, " +
            "m.content, m.type, m.status, m.created_at AS createdAt " +
            "FROM messages m " +
            "JOIN users u ON u.id = m.sender_id " +
            "WHERE m.receiver_id = #{receiverId} AND m.type = 2 AND u.role = 2 " +
            "ORDER BY m.created_at DESC LIMIT 50")
    List<Map<String, Object>> listReceivedTeacherReplies(@Param("receiverId") Long receiverId);

    @Update("UPDATE messages m JOIN users u ON u.id = m.sender_id " +
            "SET m.status = 1, m.read_at = NOW() " +
            "WHERE m.receiver_id = #{receiverId} AND m.type = 2 AND u.role = 2 AND m.status = 0")
    int markTeacherRepliesRead(@Param("receiverId") Long receiverId);
}
