package com.wen.thumbsystembackend.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogVO {  
      
    private Long id;  
  
    /**  
     * 标题  
     */  
    private String title;  
  
    /**  
     * 封面  
     */  
    private String coverImg;  
  
    /**  
     * 内容  
     */  
    private String content;  
  
    /**  
     * 点赞数  
     */  
    private Long thumbCount;
  
    /**  
     * 创建时间  
     */  
    private LocalDateTime createTime;
  
    /**  
     * 是否已点赞  
     */  
    private Boolean hasThumb;
  
}
