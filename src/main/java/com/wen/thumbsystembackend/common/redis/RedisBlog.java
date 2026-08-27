package com.wen.thumbsystembackend.common.redis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
public class RedisBlog {

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
     * 博客的点赞数
     */
    Long thumbCount;
    /**
     * 创建时间
     */
    LocalDateTime createTime;
    /**
     * 更新时间
     */
    LocalDateTime updateTime;
}
