package com.wen.thumbsystembackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "博客实体")
public class Blog {
    /**
     * 博客的id
     */
    @TableId(type = IdType.AUTO)
    Long id;
    /**
     * 用户id
     */
    Long uerId;
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
