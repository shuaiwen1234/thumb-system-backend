package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.constant.MqConstant;
import com.wen.thumbsystembackend.constant.RedisLuaScriptConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.enums.LuaStatusEnum;
import com.wen.thumbsystembackend.listener.thumb.ThumbEvent;
import com.wen.thumbsystembackend.manager.cache.CacheManager;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.RedisKeyUtil;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.amqp.rabbit.core.BatchingRabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service("thumbServiceMQ")
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper,Thumb> implements ThumbService {
    @Autowired
    private RedisTemplate<String,Object> redistemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CacheManager  cacheManager;
    @Autowired
    private BatchingRabbitTemplate batchingRabbitTemplate;

    @Override
    public BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        //幂等性 这里通过数据库的唯一索引实现
        //判断用户是否点过赞 并点赞
        Long isThumb = redistemplate.execute(RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId);
        if(isThumb.equals(LuaStatusEnum.FAIL.getValue())){
            throw new RuntimeException("该用户已点过赞");
        }
        //构造点赞事件并向mq发布
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.INCR)
                .eventTime(LocalDateTime.now())
                .build();

        //更新本地缓存(如果存在)
        cacheManager.putIfPresent(userThumbKey,blogId.toString(),1);
        batchingRabbitTemplate.convertAndSend(MqConstant.THUMB_EXCHANGE_NAME, MqConstant.THUMB_DOTHUMB_BINDINGKEY,thumbEvent);
        return ResultUtils.success(true);
    }

    @Override
    public BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        //判断用户是否为未点赞 并取消点赞
        Long isUnThumb = redistemplate.execute(RedisLuaScriptConstant.UN_THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId);
        if(isUnThumb.equals(LuaStatusEnum.FAIL.getValue())){
            throw new RuntimeException("该用户还未点赞");
        }
        //构造点赞事件并向mq发送
        ThumbEvent unThmbEvent = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.DECR)
                .eventTime(LocalDateTime.now())
                .build();

        //更新本地缓存(如果存在)
        cacheManager.putIfPresent(userThumbKey,blogId.toString(),0);
        batchingRabbitTemplate.convertAndSend(MqConstant.THUMB_EXCHANGE_NAME,MqConstant.THUMB_UNDOTHUMB_BINDINGKEY,unThmbEvent);
        return ResultUtils.success(true);
    }

    @Override
    public Boolean undoThumbOperation(Long userId, Long blogId) {
        return ThumbService.super.undoThumbOperation(userId, blogId);
    }

    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        String key = blogId.toString();
        Object result = cacheManager.get(userThumbKey, key);
        if(result != null){
            //判断是否点过赞
            if(Long.valueOf(result.toString()).equals(ThumbConstant.UN_THUMB_CONSTANT)){
                //此时为未点赞
                return false;
            }
            return true;
        }
        return redistemplate.opsForHash().hasKey(userThumbKey, key);

    }
}
