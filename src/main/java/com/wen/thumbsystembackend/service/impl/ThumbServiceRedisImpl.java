package com.wen.thumbsystembackend.service.impl;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.common.redis.RedisBlog;
import com.wen.thumbsystembackend.common.redis.RedisLogicData;
import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.RedisLuaScriptConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.enums.LuaStatusEnum;
import com.wen.thumbsystembackend.manager.cache.CacheManager;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.RedisKeyUtil;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * @author zhangziwen
 */
@Service(value="thumbService")
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CacheManager cacheManager;

    @Override
    public BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();

        //thumb:temp:{timeSlice}
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(DateUtil.format(DateUtil.date(),"yyyyMMddHH:mm"));
        //thumb:{userId}
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId, blogId
        );
        if(result .equals(LuaStatusEnum.FAIL.getValue())){
            throw new RuntimeException("该用户已点赞");
        }
        //尝试更新本地缓存
        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        String key = blogId.toString();
        cacheManager.putIfPresent(hashKey,key,1L);
        return ResultUtils.success(true);


    }


    @Override
    public BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();

        //thumb:temp:{timeSlice}
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(DateUtil.format(DateUtil.date(),"yyyyMMddHH:mm"));
        //thumb:{userId}
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;


        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId, blogId
        );
        if(result.equals(LuaStatusEnum.FAIL.getValue())){
            throw new RuntimeException("该用户未点赞");
        }
        //将本地缓存里的点赞记录设置为未点赞(不能删除 删除又要重新查询redis)
        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        String key = blogId.toString();
        cacheManager.putIfPresent(hashKey,key,ThumbConstant.UN_THUMB_CONSTANT);
        return ResultUtils.success(true);

    }


    //判断是否点赞
    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        //先从本地缓存查询
        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        String key = blogId.toString();
        Object object = cacheManager.get(hashKey, key);
        //查到了
        if(object != null){
            //判断是否点过赞
            if(Long.valueOf(object.toString()).equals(ThumbConstant.UN_THUMB_CONSTANT)){
                //此时为未点赞
                return false;
            }
            return true;
        }

        //没查到,从redis里查
        Boolean b = redisTemplate.opsForHash().hasKey(hashKey, key);
        return b;
    }

}
