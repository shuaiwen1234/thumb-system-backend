package com.wen.thumbsystembackend.service.impl;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.constant.RedisLuaScriptConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.enums.LuaStatusEnum;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.RedisKeyUtil;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Arrays;

/**
 * @author zhangziwen
 */
@Service
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();

        String tempThumbKey = RedisKeyUtil.getTempThumbKey(DateUtil.format(DateUtil.date(),"yyyyMMddHH:mm"));
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;


        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId, blogId
        );
        if(result .equals(LuaStatusEnum.FAIL.getValue())){
            throw new RuntimeException("该用户已点赞");
        }
        return ResultUtils.success(true);


    }


    @Override
    public BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();

        String tempThumbKey = RedisKeyUtil.getTempThumbKey(DateUtil.format(DateUtil.date(),"yyyyMMddHH:mm"));
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId, blogId
        );
        if(result.equals(LuaStatusEnum.FAIL.getValue())){
            throw new RuntimeException("该用户未点赞");
        }
        return ResultUtils.success(true);

    }

}
