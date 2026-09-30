package com.academic.service.impl;

import com.academic.common.entity.User;
import com.academic.entity.Message;
import com.academic.mapper.MessageMapper;
import com.academic.mapper.UserMapper;
import com.academic.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public Message getById(Long id) {
        return messageMapper.selectById(id);
    }

    public Message getMessageById(Long id) {
        return messageMapper.selectById(id);
    }

    @Override
    public boolean save(Message message) {
        return messageMapper.insert(message) > 0;
    }

    @Override
    public boolean updateById(Message message) {
        return messageMapper.updateById(message) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return messageMapper.deleteById(id) > 0;
    }

    @Override
    public Page<Message> page(Page<Message> page, QueryWrapper<Message> queryWrapper) {
        return messageMapper.selectPage(page, queryWrapper);
    }

    @Override
    public List<Message> list(QueryWrapper<Message> queryWrapper) {
        return messageMapper.selectList(queryWrapper);
    }

    @Override
    public long count(QueryWrapper<Message> queryWrapper) {
        return messageMapper.selectCount(queryWrapper);
    }

    @Override
    public long count() {
        return messageMapper.selectCount(null);
    }

    @Override
    public List<Message> getBySenderId(Long senderId) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sender_id", senderId);
        queryWrapper.orderByDesc("created_at");
        return messageMapper.selectList(queryWrapper);
    }

    @Override
    public List<Message> getByReceiverId(Long receiverId) {
        log.info("查询消息，receiverId: {}", receiverId);
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", receiverId);
        queryWrapper.orderByDesc("created_at");
        List<Message> result = messageMapper.selectList(queryWrapper);
        log.info("查询结果数量: {}", result.size());
        return result;
    }

    @Override
    public List<Message> getByType(Integer type) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", type);
        queryWrapper.orderByDesc("created_at");
        return messageMapper.selectList(queryWrapper);
    }

    @Override
    public List<Message> getByStatus(Integer status) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", status);
        queryWrapper.orderByDesc("created_at");
        return messageMapper.selectList(queryWrapper);
    }

    @Override
    public List<Message> getByReceiverIdAndStatus(Long receiverId, Integer status) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", receiverId).eq("status", status);
        queryWrapper.orderByDesc("created_at");
        return messageMapper.selectList(queryWrapper);
    }

    @Override
    public boolean saveBatch(List<Message> messages) {
        for (Message message : messages) {
            messageMapper.insert(message);
        }
        return true;
    }

    @Override
    public boolean updateStatusBatch(List<Long> ids, Integer status) {
        for (Long id : ids) {
            Message message = new Message();
            message.setId(id);
            message.setStatus(status);
            messageMapper.updateById(message);
        }
        return true;
    }

    @Override
    public long getUnreadCount(Long receiverId) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", receiverId).eq("status", 0);
        return messageMapper.selectCount(queryWrapper);
    }

    /**
     * 填充消息的发送者名称
     */
    private void fillSenderNames(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        List<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        if (senderIds.isEmpty()) {
            return;
        }
        
        List<User> users = userMapper.selectBatchIds(senderIds);
        Map<Long, String> userNameMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        
        for (Message message : messages) {
            if (message.getSenderId() != null) {
                String name = userNameMap.get(message.getSenderId());
                message.setSenderName(name != null ? name : "系统");
            }
        }
    }
    
    @Override
    public List<Message> getMessagesByUserId(Long userId) {
        log.info("getMessagesByUserId 被调用，userId: {}", userId);
        List<Message> messages = getByReceiverId(userId);
        fillSenderNames(messages);
        return messages;
    }

    @Override
    public List<Message> getAllMessages() {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_at");
        List<Message> messages = messageMapper.selectList(queryWrapper);
        // 与 getMessagesByUserId 一样补上发送人姓名 —— 管理员看的是"谁发的广播"，
        // 只有 senderId（1）在页面上没法用。原来这条路径不补，所以即便查到了
        // 消息，发送人一列也是空的。
        fillSenderNames(messages);
        return messages;
    }

    @Override
    public Message createMessage(Message message) {
        message.setCreatedAt(LocalDateTime.now());
        message.setStatus(0);
        messageMapper.insert(message);
        return message;
    }

    @Override
    public void markAsRead(Long id) {
        Message message = new Message();
        message.setId(id);
        message.setStatus(1);
        message.setReadAt(LocalDateTime.now());
        messageMapper.updateById(message);
    }

    @Override
    public void deleteMessage(Long id) {
        messageMapper.deleteById(id);
    }

    @Override
    public List<Message> getUnreadMessages(Long userId) {
        return getByReceiverIdAndStatus(userId, 0);
    }

    @Override
    public void markAllAsRead(Long userId) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", userId).eq("status", 0);
        List<Message> messages = messageMapper.selectList(queryWrapper);
        for (Message message : messages) {
            message.setStatus(1);
            message.setReadAt(LocalDateTime.now());
            messageMapper.updateById(message);
        }
    }

    @Override
    public void sendMessage(Long userId, String content, String type) {
        Message message = new Message();
        message.setSenderId(28L);
        message.setReceiverId(userId);
        message.setContent(content);
        message.setType(type != null ? Integer.parseInt(type) : 0);
        message.setStatus(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        
        // 填充发送者名称
        User sender = userMapper.selectById(28L);
        if (sender != null) {
            message.setSenderName(sender.getName());
        } else {
            message.setSenderName("系统");
        }
        
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/messages",
                    message
            );
            log.info("实时消息推送成功: userId={}, content={}", userId, content);
        } catch (Exception e) {
            log.warn("实时消息推送失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void deleteMessagesByUserId(Long userId) {
        log.info("Deleting all messages for user: {}", userId);
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sender_id", userId).or().eq("receiver_id", userId);
        messageMapper.delete(queryWrapper);
        log.info("Deleted messages for user: {}", userId);
    }
}
