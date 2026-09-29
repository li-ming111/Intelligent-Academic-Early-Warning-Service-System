package com.academic.controller;

import com.academic.common.dto.ApiResponse;
import com.academic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private AuthController authController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testLogin_Success() {
        // 准备测试数据
        Map<String, Object> request = new HashMap<>();
        request.put("username", "2023020616");
        request.put("password", "123456");
        
        // 模拟行为
        when(userService.login(request)).thenReturn(new Object());
        
        // 执行测试
        ApiResponse<Object> result = authController.login(request);
        
        // 验证结果
        assertNotNull(result);
        
        // 验证调用
        verify(userService, times(1)).login(request);
    }
    
    @Test
    void testRegister_Success() {
        // 准备测试数据
        Object request = new Object();
        
        // 模拟行为
        when(userService.register(request)).thenReturn("注册成功");
        
        // 执行测试
        ApiResponse<String> result = authController.register(request);
        
        // 验证结果
        assertNotNull(result);
        
        // 验证调用
        verify(userService, times(1)).register(request);
    }
    
    @Test
    void testGetUserInfo_Success() {
        // 准备测试数据
        Long userId = 1L;
        Object userInfo = new Object();
        
        // 模拟行为
        when(userService.getUserInfo(userId)).thenReturn(userInfo);
        
        // 执行测试
        ApiResponse<Object> result = authController.getUserInfo(userId);
        
        // 验证结果
        assertNotNull(result);
        
        // 验证调用
        verify(userService, times(1)).getUserInfo(userId);
    }
}