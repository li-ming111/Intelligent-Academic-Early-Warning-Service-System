package com.academic.service;

import com.academic.common.dto.ApiResponse;
import com.academic.common.entity.Message;
import com.academic.common.entity.User;
import com.academic.feign.MessageServiceClient;
import com.academic.mapper.MessageMapper;
import com.academic.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师站内信收件箱。数据源是 {@code messages} 表（辅导员私信、管理员广播），
 * 与学生反馈（{@code feedback}）是两套东西，不要再混在一起。
 */
@Service
public class TeacherInboxService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MessageServiceClient messageServiceClient;

    public List<Map<String, Object>> getInbox(Long userId, String type) {
        QueryWrapper<Message> query = new QueryWrapper<>();
        query.eq("receiver_id", userId);
        if (type != null && !type.trim().isEmpty()) {
            try {
                query.eq("type", Integer.parseInt(type.trim()));
            } catch (NumberFormatException ignored) {
                // 非数字类型直接忽略筛选，等同于查全部
            }
        }
        query.orderByDesc("created_at");
        List<Message> messages = messageMapper.selectList(query);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> senderNames = new HashMap<>();
        Map<Long, Integer> senderRoles = new HashMap<>();
        if (!senderIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(senderIds)) {
                if (user.getName() != null && !user.getName().isEmpty()) {
                    senderNames.put(user.getId(), user.getName());
                }
                senderRoles.put(user.getId(), user.getRole());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", message.getId());
            item.put("senderId", message.getSenderId());
            item.put("senderName", senderNames.getOrDefault(message.getSenderId(), "系统"));
            // 前端据此决定是否显示「回复」：只有辅导员私信（type=2 且发送方 role=3）可回复。
            item.put("senderRole", senderRoles.get(message.getSenderId()));
            item.put("title", message.getTitle());
            item.put("content", message.getContent());
            item.put("type", message.getType());
            item.put("status", message.getStatus());
            item.put("createdAt", message.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    public long getUnreadCount(Long userId) {
        QueryWrapper<Message> query = new QueryWrapper<>();
        query.eq("receiver_id", userId).eq("status", 0);
        return messageMapper.selectCount(query);
    }

    /**
     * 只允许收件人标记自己的消息；已读的消息重复标记视为成功，不报错。
     */
    public boolean markRead(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null || !userId.equals(message.getReceiverId())) {
            return false;
        }
        if (message.getStatus() != null && message.getStatus() == 1) {
            return true;
        }
        UpdateWrapper<Message> update = new UpdateWrapper<>();
        update.eq("id", messageId)
                .eq("receiver_id", userId)
                .set("status", 1)
                .set("read_at", LocalDateTime.now());
        return messageMapper.update(null, update) > 0;
    }

    public int markAllRead(Long userId) {
        UpdateWrapper<Message> update = new UpdateWrapper<>();
        update.eq("receiver_id", userId)
                .eq("status", 0)
                .set("status", 1)
                .set("read_at", LocalDateTime.now());
        return messageMapper.update(null, update);
    }

    /**
     * 回复一条辅导员私信。
     *
     * <p>回复写回 {@code messages} 表（{@code sender_id}=教师、{@code receiver_id}=原发送人、
     * {@code type=2}），由 message-service 统一落库并实时推送给辅导员 ——
     * 这样辅导员端能收到，而不是只在本页弹一个"已回复"。
     *
     * <p>只允许原收件人回复，且原消息必须是辅导员发来的私信；回复的同时把原消息标记为已读。
     */
    public void reply(Long userId, Long messageId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        if (text.length() > 500) {
            throw new IllegalArgumentException("回复内容不能超过 500 字");
        }

        Message original = messageMapper.selectById(messageId);
        if (original == null || !userId.equals(original.getReceiverId())) {
            throw new NoSuchElementException("消息不存在或不属于当前用户");
        }
        if (original.getType() == null || original.getType() != 2) {
            throw new IllegalArgumentException("只有辅导员私信可以回复");
        }

        Long receiverId = original.getSenderId();
        User counselor = receiverId == null ? null : userMapper.selectById(receiverId);
        if (counselor == null || counselor.getRole() == null || counselor.getRole() != 3) {
            throw new IllegalArgumentException("原消息发送方不是辅导员，无法回复");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", receiverId);
        payload.put("content", text);
        payload.put("type", 2);

        ApiResponse<String> result;
        try {
            result = messageServiceClient.sendMessage(payload);
        } catch (Exception e) {
            throw new IllegalStateException("消息服务不可用，回复发送失败", e);
        }
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException(result == null ? "消息服务无响应" : result.getMessage());
        }

        markRead(userId, messageId);
    }
}
