package com.wen.thumbsystembackend.common.redis;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "redis保存的博客实体")
public class RedisBlog {
    /**
     * 博客的id
     */
    Long id;
    /**
     * 用户id(作者)
     */
    Long userId;
    /**
     * 博客标题
     */
    String title;
    /**
     * 博客封面
     */
    String coverImg;
    /**
     * 博客内容
     */
    String content;
    /**
     * 创建时间
     */
    LocalDateTime createTime;
    /**
     * 更新时间
     */
    LocalDateTime updateTime;
}
