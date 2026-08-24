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
@Schema(description = "点赞实体")
public class Thumb {
    /**
    点赞记录的id
     */
    @TableId(type = IdType.AUTO)
    Long id;
    /**
     用户id
     */
    Long userId;
    /**
     * 博客id
     */
    Long blogId;
    /**
     * 创建时间
     */
    LocalDateTime createTime;
}
