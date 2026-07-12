package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.HotQuestion;
import com.lhs.lawmind.mapper.HotQuestionMapper;
import com.lhs.lawmind.service.HotQuestionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 热点问题缓存服务测试类
 */
@ExtendWith(MockitoExtension.class)
class HotQuestionCacheServiceImplTest {

    @Mock
    private HotQuestionMapper hotQuestionMapper;

    @Mock
    private RagConfig ragConfig;

    @InjectMocks
    private HotQuestionCacheServiceImpl hotQuestionCacheService;

    private HotQuestion sampleHotQuestion;
    private String testQuestionMd5;

    @BeforeEach
    void setUp() {
        testQuestionMd5 = "test-md5-hash";
        
        sampleHotQuestion = new HotQuestion();
        sampleHotQuestion.setId(1L);
        sampleHotQuestion.setQuestionMd5(testQuestionMd5);
        sampleHotQuestion.setOriginalQuestion("测试问题");
        sampleHotQuestion.setCachedAnswer("测试答案");
        sampleHotQuestion.setVisitCount(5);
        sampleHotQuestion.setFirstVisitTime(new Date());
        sampleHotQuestion.setLastVisitTime(new Date());
        sampleHotQuestion.setCreateTime(new Date());
        
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 30);
        sampleHotQuestion.setExpireTime(calendar.getTime());
        sampleHotQuestion.setStatus(0);
    }

    @Test
    void testGetHotQuestionByMd5_Success() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(sampleHotQuestion);

        // When
        HotQuestion result = hotQuestionCacheService.getHotQuestionByMd5(testQuestionMd5);

        // Then
        assertNotNull(result);
        assertEquals(testQuestionMd5, result.getQuestionMd5());
        assertEquals("测试答案", result.getCachedAnswer());
        verify(hotQuestionMapper).selectByQuestionMd5(testQuestionMd5);
    }

    @Test
    void testGetHotQuestionByMd5_NotFound() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(null);

        // When
        HotQuestion result = hotQuestionCacheService.getHotQuestionByMd5(testQuestionMd5);

        // Then
        assertNull(result);
        verify(hotQuestionMapper).selectByQuestionMd5(testQuestionMd5);
    }

    @Test
    void testGetHotQuestionByMd5_EmptyMd5() {
        // When
        HotQuestion result = hotQuestionCacheService.getHotQuestionByMd5("");

        // Then
        assertNull(result);
    }

    @Test
    void testIsHotQuestion_True() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(sampleHotQuestion);

        // When
        boolean result = hotQuestionCacheService.isHotQuestion(testQuestionMd5);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsHotQuestion_False() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(null);

        // When
        boolean result = hotQuestionCacheService.isHotQuestion(testQuestionMd5);

        // Then
        assertFalse(result);
    }

    @Test
    void testGetHotAnswer_Success() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(sampleHotQuestion);

        // When
        String result = hotQuestionCacheService.getHotAnswer(testQuestionMd5);

        // Then
        assertEquals("测试答案", result);
    }

    @Test
    void testGetHotAnswer_NotFound() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(null);

        // When
        String result = hotQuestionCacheService.getHotAnswer(testQuestionMd5);

        // Then
        assertNull(result);
    }

    @Test
    void testUpgradeToHotCache_NewRecord() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(null);
        when(hotQuestionMapper.insert(any(HotQuestion.class))).thenReturn(1);

        // When
        boolean result = hotQuestionCacheService.upgradeToHotCache(
            testQuestionMd5, "测试问题", "测试答案", "1,2,3", 30);

        // Then
        assertTrue(result);
        verify(hotQuestionMapper).insert(any(HotQuestion.class));
    }

    @Test
    void testUpgradeToHotCache_AlreadyExists() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(sampleHotQuestion);
        when(hotQuestionMapper.update(any(HotQuestion.class))).thenReturn(1);

        // When
        boolean result = hotQuestionCacheService.upgradeToHotCache(
            testQuestionMd5, "测试问题", "测试答案", "1,2,3", 30);

        // Then
        assertTrue(result);
        verify(hotQuestionMapper).update(any(HotQuestion.class)); // 应该更新访问次数
    }

    @Test
    void testUpgradeToHotCache_InvalidParameters() {
        // When
        boolean result = hotQuestionCacheService.upgradeToHotCache("", "测试问题", "", "1,2,3", 30);

        // Then
        assertFalse(result);
        verify(hotQuestionMapper, never()).selectByQuestionMd5(any());
    }

    @Test
    void testUpdateVisitCount_Success() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(sampleHotQuestion);
        when(hotQuestionMapper.update(any(HotQuestion.class))).thenReturn(1);

        // When
        int result = hotQuestionCacheService.updateVisitCount(testQuestionMd5);

        // Then
        assertEquals(6, result); // 原来是5，+1后应该是6
        verify(hotQuestionMapper).update(any(HotQuestion.class));
    }

    @Test
    void testUpdateVisitCount_NotFound() {
        // Given
        when(hotQuestionMapper.selectByQuestionMd5(testQuestionMd5)).thenReturn(null);

        // When
        int result = hotQuestionCacheService.updateVisitCount(testQuestionMd5);

        // Then
        assertEquals(0, result);
        verify(hotQuestionMapper).selectByQuestionMd5(testQuestionMd5);
        verify(hotQuestionMapper, never()).update(any());
    }

    @Test
    void testCleanupExpiredCache_Success() {
        // Given
        HotQuestion expiredQuestion = new HotQuestion();
        expiredQuestion.setId(2L);
        expiredQuestion.setQuestionMd5("expired-md5");
        
        when(hotQuestionMapper.selectExpiredHotQuestions(any(Date.class)))
            .thenReturn(java.util.Arrays.asList(expiredQuestion));
        when(hotQuestionMapper.batchUpdateStatus(anyList(), eq(1))).thenReturn(1);

        // When
        int result = hotQuestionCacheService.cleanupExpiredCache();

        // Then
        assertEquals(1, result);
        verify(hotQuestionMapper).batchUpdateStatus(anyList(), eq(1));
    }

    @Test
    void testCleanupExpiredCache_NoExpired() {
        // Given
        when(hotQuestionMapper.selectExpiredHotQuestions(any(Date.class)))
            .thenReturn(java.util.Collections.emptyList());

        // When
        int result = hotQuestionCacheService.cleanupExpiredCache();

        // Then
        assertEquals(0, result);
        verify(hotQuestionMapper, never()).batchUpdateStatus(anyList(), anyInt());
    }

    @Test
    void testGetCacheCount_Success() {
        // Given
        when(hotQuestionMapper.countTotal()).thenReturn(10);

        // When
        int result = hotQuestionCacheService.getCacheCount();

        // Then
        assertEquals(10, result);
        verify(hotQuestionMapper).countTotal();
    }

    @Test
    void testGetCacheCount_Exception() {
        // Given
        when(hotQuestionMapper.countTotal()).thenThrow(new RuntimeException("Database error"));

        // When
        int result = hotQuestionCacheService.getCacheCount();

        // Then
        assertEquals(0, result);
    }
}