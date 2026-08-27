package com.wen.thumbsystembackend.common.redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author zhangziwen
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisLogicData<T> {
    /**
     * 数据
     */
    private T data;
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
}
