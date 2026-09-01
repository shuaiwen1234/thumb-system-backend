package com.wen.thumbsystembackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;

public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest 内部封装了博客id
     * @return
     */
    BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest);

    /**
     * 封装点赞之后对数据表进行更新的一系列逻辑
     * @param userId 用户id
     * @param blogId 博客id
     * @return 插入点赞记录表之后返回的记录的id
     */
    default public Long doThumbOperation(Long userId, Long blogId){
        return -1L;
    };

    /**
     * 取消点赞
     * @param doThumbRequest 内部封装了博客id
     * @return
     */
    BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest);

    /**
     * 封装取消点赞之后对数据表进行更新的一系列逻辑
     * @param userId 用户id
     * @param blogId 博客id
     * @return
     */
    default public Boolean undoThumbOperation(Long userId, Long blogId){
        return Boolean.FALSE;
    }

    /**
     * 使用redis判断是否存在对应的点赞记录
     * redis中的结构是key: thumb:+userId  field: blogId  value: thumbId
     * @param userId 用户id
     * @param blogId 博客id
     * @return
     */
    default Boolean hasThumb(Long userId,Long blogId){
        return false;
    }


}
