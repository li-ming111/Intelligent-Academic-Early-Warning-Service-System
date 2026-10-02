package com.academic.service;

import com.academic.entity.Message;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface MessageService {
    Message getById(Long id);
    Message getMessageById(Long id);
    boolean save(Message message);
    boolean updateById(Message message);
    boolean removeById(Long id);
    Page<Message> page(Page<Message> page, QueryWrapper<Message> queryWrapper);
    List<Message> list(QueryWrapper<Message> queryWrapper);
    long count(QueryWrapper<Message> queryWrapper);
    long count();
    List<Message> getBySenderId(Long senderId);
    List<Message> getByReceiverId(Long receiverId);
    List<Message> getByType(Integer type);
    List<Message> getByStatus(Integer status);
    List<Message> getByReceiverIdAndStatus(Long receiverId, Integer status);
    boolean saveBatch(List<Message> messages);
    boolean updateStatusBatch(List<Long> ids, Integer status);
    long getUnreadCount(Long receiverId);

    List<Message> getMessagesByUserId(Long userId);
    List<Message> getAllMessages();
    Message createMessage(Message message);
    void markAsRead(Long id);
    void deleteMessage(Long id);
    List<Message> getUnreadMessages(Long userId);
    void markAllAsRead(Long userId);
    /**
     * 定向发送一条消息并实时推送。
     *
     * @param senderId 发件人 {@code users.id}（由调用方从身份头解析后传入）。
     *                 2026-10-02（W3.2）之前没有这个参数 —— 实现里写死 {@code 28L}，
     *                 于是每条定向消息的发件人都是 28 号用户，且**不报错**。
     */
    void sendMessage(Long senderId, Long userId, String content, String type);
    // deleteMessagesByUserId(Long userId) 已删（2026-10-01）：它的唯一调用方
    // （`MessageController` 的 `DELETE /user/{userId}`）也一并删了，见那边的说明。
}
