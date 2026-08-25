package com.wen.thumbsystembackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;

public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest
     * @return
     */
    BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest);

    /**
     * 封装点赞之后对数据表进行更新的一系列逻辑
     * @param userId
     * @param blogId
     * @return
     */
    public boolean doThumbOperation(Long userId, Long blogId);

    /**
     * 取消点赞
     * @param doThumbRequest
     * @return
     */
    BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest);

    /**
     * 封装取消点赞之后对数据表进行更新的一系列逻辑
     * @param userId
     * @param blogId
     * @return
     */
    public boolean undoThumbOperation(Long userId, Long blogId);
}
