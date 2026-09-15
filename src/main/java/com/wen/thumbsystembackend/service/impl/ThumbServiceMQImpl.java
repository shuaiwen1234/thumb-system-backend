package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.constant.MqConstant;
import com.wen.thumbsystembackend.constant.RedisLuaScriptConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.enums.LuaStatusEnum;
import com.wen.thumbsystembackend.listener.thumb.ThumbEvent;
import com.wen.thumbsystembackend.manager.cache.CacheManager;
import com.wen.thumbsystembackend.mapper.BlogMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private BlogMapper blogMapper;

    @Override
    public BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        //先检查博客是否存在
        Blog blog = blogMapper.selectById(blogId);
        if(blog==null){
            throw new RuntimeException("该博客不存在 请稍后再试");
        }

        //幂等性 这里通过数据库的唯一索引实现
        //判断用户是否点过赞 并点赞
        Long isThumb = redistemplate.execute(RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId);
        if(isThumb.equals(LuaStatusEnum.FAIL.getValue())){
            // 增加热度
            cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),1);
            throw new RuntimeException("该用户已点过赞");
        }
        //仅当 Redis 无记录(可能过期)时才查 DB 兜底；Redis 明确为 0(未赞)时直接放行，避免与取消点赞的异步删库竞态
        if(isThumb.equals(ThumbConstant.THUMB_REDIS_MISSED)){
            Thumb thumb = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getBlogId, blogId).eq(Thumb::getUserId, userId));
            if(thumb != null){
                // 增加热度
                cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),1);
                throw new RuntimeException("该用户已点过赞");
            }
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
        batchingRabbitTemplate.convertAndSend(MqConstant.THUMB_EXCHANGE_NAME, MqConstant.THUMB_BINDING_KEY, thumbEvent);
        // 增加热度
        cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),1);
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
            // 增加热度
            cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),0);
            throw new RuntimeException("该用户还未点赞");
        }
        //仅当 Redis 无记录(可能过期)时才查 DB 兜底；Redis 明确为 1(已赞)时直接放行，避免与点赞的异步落库竞态
        if(isUnThumb.equals(ThumbConstant.THUMB_REDIS_MISSED)){
            Thumb thumb = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getBlogId, blogId).eq(Thumb::getUserId, userId));
            if(thumb == null){
                // 增加热度
                cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),0);
                throw new RuntimeException("该用户还未点赞");
            }
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
        batchingRabbitTemplate.convertAndSend(MqConstant.THUMB_EXCHANGE_NAME, MqConstant.THUMB_BINDING_KEY, unThmbEvent);
        // 增加热度
        cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),0);
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
        Long value = null;
        //因为本地缓存的存活时间比redis里的缓存的存活时间短 且更新本地缓存和更新redis是同步的 即他们同时刷新存活时长
        //所以能查到本地缓存redis里就一定有数据
        if(result != null){
            value = Long.valueOf(result.toString());
        }

        if(result == null){
            //本地缓存没有 去查redis
            result = redistemplate.opsForHash().get(userThumbKey, key);
            if(result != null){
                value = Long.valueOf(result.toString());
            }
        }

        if(result == null){
            //redis没有去查数据库
            Thumb thumb = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getBlogId, blogId).eq(Thumb::getUserId, userId));
            if(thumb == null){
                value = ThumbConstant.UN_THUMB_CONSTANT;
            }else {
                value = ThumbConstant.THUMB_CONSTANT;
            }
            /*
            t0  hasThumb：本地 miss → Redis HGET → 没这个 field（过期了）→ 准备查 DB
t1  doThumb（用户同时点了赞）：Lua 发现 field 不存在 → HSET 1 + EXPIRE → 发 INCR 事件
                                                              ↑ field 此刻诞生了
t2  hasThumb：DB 查询 → 查不到行（MQ 还没落库，最长 10 秒窗口）→ 算出 value = 0
t3  hasThumb：回写
       put        → 把 t1 写的 1 抹成 0  ❌
       putIfAbsent→ 发现 field 已存在 → 跳过，1 保住 ✓
             */
            //所以用putIfAbsent
            redistemplate.opsForHash().putIfAbsent(userThumbKey, key, value);
        }
        redistemplate.expire(userThumbKey,10,TimeUnit.DAYS);
        cacheManager.recordThumbHit(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),value);
        return value.equals(ThumbConstant.THUMB_CONSTANT);

    }

    /**
     * 查询这个用户点过赞的博客的id
     * @param userId 用户id
     * @return
     */
    @Override
    public List<Long> thumbedList(Long userId) {

        List<Long> blogIds = thumbMapper.selectThumbedBlogList(userId);
        return  blogIds;
    }
}
