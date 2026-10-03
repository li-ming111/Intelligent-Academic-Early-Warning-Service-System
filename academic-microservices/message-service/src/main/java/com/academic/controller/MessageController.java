package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.entity.Message;
import com.academic.mapper.UserMapper;
import com.academic.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/messages", "/students/messages", "/teachers/messages"})
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserMapper userMapper;

    /** 从 GateWay 注入的头获取请求者 userId */
    private Long getRequestUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        if (header != null) {
            try { return Long.parseLong(header); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-Role");
        return "4".equals(role) || "admin".equals(role);
    }

    @GetMapping
    public ApiResponse<List<Message>> getMessages(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") Boolean unreadOnly,
            HttpServletRequest request) {
        Long reqUserId = getRequestUserId(request);
        // 非管理员只能查看自己的消息
        if (userId != null && !userId.equals(reqUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权查看其他用户的消息");
        }
        // 看谁的消息，三种情况：
        //   · 明确传了 userId（只有管理员能传别人）→ 看那个人的收件箱
        //   · 没传 userId 且是管理员                → 看**全部**（admin-service 的「消息管理」
        //     页要的是广播历史，不是管理员自己的收件箱）
        //   · 其它                                  → 看自己的
        // ⚠️ 原来最后一种把管理员也并进去了：effectiveUserId = reqUserId = 管理员自己，
        //    于是走 getMessagesByUserId(1)。后果是**广播写明写进去了，管理页面还是 0 条**：
        //    2026-09-29 实测：/admin/messages/broadcast targetRole=5 确实往 messages
        //    写入了 2 行（sender_id=1, receiver_id=56/57），而 /admin/messages/list
        //    返回 total=0 —— 因为管理员查的是"发给自己的消息"，广播是发给别人的。
        //    下面那个 `else`（getAllMessages）本来就是为"不指定用户"准备的，
        //    只是在这之前 reqUserId 永远不是 null，这个分支对管理员根本走不到。
        Long effectiveUserId;
        if (userId != null) {
            effectiveUserId = isAdmin(request) ? userId : reqUserId;
        } else {
            effectiveUserId = isAdmin(request) ? null : reqUserId;
        }
        List<Message> messages;
        if (unreadOnly && effectiveUserId != null) {
            messages = messageService.getUnreadMessages(effectiveUserId);
        } else if (effectiveUserId != null) {
            messages = messageService.getMessagesByUserId(effectiveUserId);
        } else if (unreadOnly) {
            // 管理员 + 只看未读 + 不限用户：getAllMessages 没有 unreadOnly 版本，
            // 自己按 status=0 筛（与 getUnreadMessages 同一口径：0 就是未读）
            messages = messageService.getAllMessages().stream()
                    .filter(m -> m.getStatus() == null || m.getStatus() == 0)
                    .collect(java.util.stream.Collectors.toList());
        } else {
            messages = messageService.getAllMessages();
        }
        return ApiResponse.success(messages);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<Message> getMessageById(@PathVariable Long id) {
        Message message = messageService.getMessageById(id);
        if (message != null) {
            return ApiResponse.success(message);
        }
        return ApiResponse.notFound("消息不存在");
    }

    @PostMapping
    public ApiResponse<Message> createMessage(@RequestBody Message message) {
        Message created = messageService.createMessage(message);
        return ApiResponse.success(created);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<String> markAsRead(@PathVariable Long id) {
        // 2026-10-03（W7）：读影响行数 —— 0 行说明该 id 不存在，如实回 404，
        // 不能再回「已标记为已读」（那是对不存在的行报成功）。
        return messageService.markAsRead(id) > 0
                ? ApiResponse.success("已标记为已读")
                : ApiResponse.notFound("消息不存在");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteMessage(@PathVariable Long id) {
        // 2026-10-03（W7）：同上，删 0 行 = 该 id 不存在 ⇒ 404（HTTP 200 + body code 404，
        // 与本仓控制器业务拒绝的惯例一致）。
        return messageService.deleteMessage(id) > 0
                ? ApiResponse.success("消息已删除")
                : ApiResponse.notFound("消息不存在");
    }
    
    // ==================== 这里原来有 `DELETE /user/{userId}`（2026-10-01 删） ====================
    //
    // 它删掉的是 `messages` 里 `sender_id = ? OR receiver_id = ?` 命中该账号的行。
    // 删它的理由有两层，任一层单独成立就够：
    //
    //   1) **零调用方。** 它当初的 javadoc 写着"供 admin-service 调用"，而那次调用
    //      （admin-service `AdminController.deleteUser` 里清站内信）已于 2026-09-30 删除 ——
    //      因为 `deleteUser` 现在先跑 `UserMapper.countReferences`，其中就有一项是
    //      `messages` 的同一个谓词，有任何一行就直接拒绝删除。也就是说能走到那次 Feign 调用的
    //      账号，messages 必然是 0 行，那个调用是空操作，只会在 message-service 没起时
    //      白白阻塞到超时（实测 12.1 秒）。调用方一删，这个端点 + Feign 方法 + fallback 实现
    //      就一起成了死代码。
    //
    //   2) **它是本控制器里少数不做归属校验的端点之一。** 本文件另外 5 个端点
    //      （`GET /`、`GET /unread/count`、`PUT /read/all`、`GET /{userId}`、
    //      `GET /{userId}/unread-count`）开头都是同一句"非管理员只能操作自己的 userId"，
    //      而这个端点**路径里直接收一个 userId，一个校验都不做**就往下删。
    //      网关那层也不拦：`/api/messages/**` 不匹配任何受限前缀，`hasPermission`
    //      对 role 1/2/3 一路落到末尾 `return true`。
    //      ⇒ 留着它，等于留一条"有 token 就能删别人全部消息"的写口，而它又没有任何调用方。
    //
    // 同批没有动的：本文件另外 8 个映射也没有归属校验（`GET /detail/{id}`、`POST /`、
    // `PUT /{id}/read`、`DELETE /{id}`、`POST /send`、`POST /{id}/mark-read`、`POST /broadcast`
    // 等），但它们与这条不同 —— 要么是 id 型（收的是消息 id 不是用户 id），
    // 要么 admin-service 正在用。**没有调用方佐证意图的那几个，改之前要先决定这条面还留不留。**

    @GetMapping("/unread/count")
    public ApiResponse<Long> getUnreadCount(@RequestParam Long userId,
                                             HttpServletRequest request) {
        Long reqUserId = getRequestUserId(request);
        if (!userId.equals(reqUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权查看其他用户的未读数");
        }
        Long effectiveUserId = isAdmin(request) ? userId : reqUserId;
        Long count = messageService.getUnreadCount(effectiveUserId);
        return ApiResponse.success(count);
    }

    @PutMapping("/read/all")
    public ApiResponse<String> markAllAsRead(@RequestParam Long userId,
                                              HttpServletRequest request) {
        Long reqUserId = getRequestUserId(request);
        if (!userId.equals(reqUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权操作其他用户的消息");
        }
        Long effectiveUserId = isAdmin(request) ? userId : reqUserId;
        messageService.markAllAsRead(effectiveUserId);
        return ApiResponse.success("所有消息已标记为已读");
    }

    @PostMapping("/send")
    public ApiResponse<String> sendMessage(@RequestBody Map<String, Object> payload,
                                           HttpServletRequest request) {
        Long userId = null;
        if (payload.containsKey("userId")) {
            userId = ((Number) payload.get("userId")).longValue();
        } else if (payload.containsKey("user_id")) {
            userId = ((Number) payload.get("user_id")).longValue();
        }

        String content = (String) payload.get("content");

        Integer typeInt = null;
        if (payload.containsKey("type")) {
            Object typeObj = payload.get("type");
            if (typeObj instanceof Integer) {
                typeInt = (Integer) typeObj;
            } else if (typeObj instanceof String) {
                typeInt = Integer.parseInt((String) typeObj);
            }
        }

        String typeStr = typeInt != null ? String.valueOf(typeInt) : "0";

        // 2026-10-02（W3.2）：发件人由**身份头**决定，不再恒为 28。
        //
        // 原来这里调用的是 `sendMessage(userId, content, type)`，而实现里写死
        // `message.setSenderId(28L)` —— 于是管理员发的每一条定向消息，在库里、
        // 在学生的通知中心里，发件人都是「28 号用户」。它不报错（28 确实存在，
        // 外键过得去），所以从症状上完全看不出来。
        //
        // 取不到身份就**如实报错**，不要塞一个假的发件人：`messages.sender_id`
        // 有指向 `users.id` 的真外键，塞 0/NULL 要么 ERROR 1452、要么留一行无主消息。
        Long senderId = getRequestUserId(request);
        if (senderId == null) {
            return ApiResponse.badRequest("无法确定发送人：请求缺少 X-User-Id（网关或调用方需注入身份）");
        }

        messageService.sendMessage(senderId, userId, content, typeStr);
        return ApiResponse.success("消息发送成功");
    }

    // ========== 兼容性端点 ==========

    // /teachers/messages/{userId} 和 /students/messages/{userId}
    @GetMapping("/{userId}")
    public ApiResponse<List<Message>> getMessagesByUserId(@PathVariable Long userId,
                                                           HttpServletRequest request) {
        Long reqUserId = getRequestUserId(request);
        if (!userId.equals(reqUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权查看其他用户的消息");
        }
        List<Message> messages = messageService.getMessagesByUserId(userId);
        return ApiResponse.success(messages);
    }

    @GetMapping("/{userId}/unread-count")
    public ApiResponse<Map<String, Object>> getUnreadCountByUserId(@PathVariable Long userId,
                                                                     HttpServletRequest request) {
        Long reqUserId = getRequestUserId(request);
        if (!userId.equals(reqUserId) && !isAdmin(request)) {
            return ApiResponse.error(403, "无权查看其他用户的未读数");
        }
        Long count = messageService.getUnreadCount(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("unreadCount", count);
        result.put("userId", String.valueOf(userId));
        return ApiResponse.success(result);
    }

    // /teachers/messages/{messageId}/mark-read 和 /students/messages/{messageId}/mark-read
    @PostMapping("/{id}/mark-read")
    public ApiResponse<String> markAsReadByPost(@PathVariable Long id) {
        // 2026-10-03（W7）：同 PUT /{id}/read，0 行 ⇒ 404
        return messageService.markAsRead(id) > 0
                ? ApiResponse.success("消息已标记为已读")
                : ApiResponse.notFound("消息不存在");
    }

    // 广播消息端点 - 供 admin-service 调用
    @PostMapping("/broadcast")
    public ApiResponse<Map<String, Object>> sendBroadcast(@RequestBody Map<String, Object> payload,
                                                          HttpServletRequest request) {
        String title = (String) payload.get("title");
        String content = (String) payload.get("content");
        Integer targetRole = payload.get("targetRole") != null ? ((Number) payload.get("targetRole")).intValue() : null;

        // ⚠️ sender_id 是 NOT NULL 且没有默认值的列。原实现没有给它赋值，
        //    后果不是"少写一个字段"，而是**每次插入都抛
        //    Field 'sender_id' doesn't have a default value**；
        //    而异常被下面那个 catch 吞掉，count 照旧是 0 ——
        //    接口照样回 success:true，只是数目不对。
        //    （2026-09-29 实测：targetRole=5 时 messages 表 0 行，
        //     message-service 日志里就是这条 SQLException。）
        //    广播的发送人就是发起的那个管理员：取网关注入的 X-User-Id。
        Long senderId = getRequestUserId(request);
        if (senderId == null) {
            // 没有该头说明不是经网关进来的调用。广播必须落到一个真实存在的账号上，
            // 不能静默写 0（0 不是合法用户，外键会挡），也不能假装成功。
            log.warn("Broadcast without X-User-Id header, fallback to system admin id=1");
            senderId = 1L;
        }

        // 目标用户查询失败是真的失败，这一层不吞
        List<com.academic.common.entity.User> users;
        if (targetRole != null) {
            users = userMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.academic.common.entity.User>()
                            .eq("role", targetRole).eq("status", 1));
        } else {
            users = userMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.academic.common.entity.User>()
                            .eq("status", 1));
        }

        // 逐个用户单独 try：一个人写失败不该让后面所有人都收不到。
        // 同时把成功/失败分开数 —— 原来的 `count++` 数的是"尝试过的人数"，
        // 不管插没插进去，所以"发送成功 35 条"这种话在那个版本里是假的。
        int sent = 0;
        int failed = 0;
        for (com.academic.common.entity.User user : users) {
            try {
                Message msg = new Message();
                msg.setSenderId(senderId);
                msg.setReceiverId(user.getId());
                msg.setTitle(title);
                msg.setContent(content);
                msg.setType(1);  // 1=广播通知
                msg.setStatus(0); // 0=未读
                msg.setCreatedAt(LocalDateTime.now());
                messageService.createMessage(msg);
                sent++;
            } catch (Exception e) {
                failed++;
                log.error("Broadcast to user {} failed: {}", user.getId(), e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", failed == 0);
        result.put("sentCount", sent);
        result.put("failedCount", failed);
        result.put("targetCount", users.size());
        result.put("message", failed == 0
                ? ("已发送给 " + sent + " 个用户")
                : ("发送完成：成功 " + sent + " 条，失败 " + failed + " 条"));
        log.info("Broadcast done: title={}, targetRole={}, sent={}, failed={}, senderId={}",
                title, targetRole, sent, failed, senderId);

        // 有目标用户却一条都没写进去 = 整体失败，不能报 200 让人以为发出去了
        if (sent == 0 && !users.isEmpty()) {
            return ApiResponse.error(500, "广播发送失败：0/" + users.size() + " 条写入成功");
        }
        return ApiResponse.success(result);
    }
}
