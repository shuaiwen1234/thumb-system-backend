package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.management.RuntimeOperationsException;

@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private BlogMapper blogMapper;

    @Override
    public BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        // 1. 判断是否点过赞
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<Thumb>();
        queryWrapper.eq(Thumb::getUserId, userId);
        queryWrapper.eq(Thumb::getBlogId, blogId);
        Thumb hasThumbed = thumbMapper.selectOne(queryWrapper);

        // 1.1 点过直接抛出异常
        if(hasThumbed!=null){
            throw new RuntimeException("该用户已对当前文档点赞,请勿重复操作");
        }

        // 1.2 调用点赞之后的更新逻辑
        boolean b;
        //这里加锁用userId的原因是锁粒度比blogId小 并且mysql的行级锁可以保证多个用户对同一个博客进行点赞时不会出现多线程安全问题
        synchronized (userId.toString().intern()) {
            //这里进行一下DoubleCheck 进一步保证安全
            //判断该用户是否点过赞
            LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<Thumb>();
            wrapper.eq(Thumb::getUserId, userId);
            wrapper.eq(Thumb::getBlogId, blogId);
            Thumb hasThumbed2 = thumbMapper.selectOne(wrapper);

            // 点过直接抛出异常
            if(hasThumbed2!=null){
                throw new RuntimeException("该用户已对当前文档点赞,请勿重复操作");
            }

            // 这里使用代理来执行点赞的方法是因为如果使用this会使this调用的方法上的事务失效
            ThumbService proxy = (ThumbService)AopContext.currentProxy();
            b = proxy.doThumbOperation(userId, blogId);
        }
        return ResultUtils.success(b);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean doThumbOperation(Long userId, Long blogId) {
        //插入点赞表
        Thumb thumb = new Thumb();
        thumb.setUserId(userId);
        thumb.setBlogId(blogId);
        int insert = thumbMapper.insert(thumb);

        //更新博客表 将对应博客的点赞数加一
        LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<Blog>();
        updateWrapper.setSql("thumb_count = thumb_count+1");
        updateWrapper.eq(Blog::getId, blogId);
        int update = blogMapper.update(updateWrapper);

        //更新失败直接抛出异常
        //只有抛出异常事务才会回滚
        if (insert <= 0 || update <= 0) {
            throw new RuntimeException("点赞失败");
        }
        return true;
    }

    @Override
    public BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        // 1.先判断当前用户是否点赞
        Thumb thumb = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId));

        // 1.1没点赞则直接抛出异常
        if(thumb==null){
            throw new RuntimeException("该用户暂时未对当前文档点赞");
        }

        // 1.2点赞了则进行取消点赞的一系列逻辑
        //博客的点赞数减一 将点赞记录删除
        boolean b;
        synchronized (userId.toString().intern()) {
            //这里也先做一下DoubleCheck
            // 判断当前用户是否点赞
            Thumb thumb1 = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId));

            // 1.1没点赞则直接抛出异常
            if(thumb1==null){
                throw new RuntimeException("该用户暂时未对当前文档点赞");
            }

            // 这里使用代理来执行点赞的方法是因为如果使用this会使this调用的方法上的事务失效
            ThumbService proxy = (ThumbService)AopContext.currentProxy();
            b = proxy.undoThumbOperation(userId, blogId);
        }
        return ResultUtils.success(b);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean undoThumbOperation(Long userId, Long blogId) {
        //删除点赞记录
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Thumb::getUserId, userId);
        wrapper.eq(Thumb::getBlogId, blogId);
        int delete = thumbMapper.delete(wrapper);
        //更新失败直接抛出异常
        //只有抛出异常 事务才会回滚
        if(delete<=0){
            throw new RuntimeException("取消点赞失败,该用户未对当前文档进行点赞");
        }

        //将博客的点赞记录减一
        LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.setSql("thumb_count = thumb_count-1");
        updateWrapper.eq(Blog::getId, blogId);
        int update = blogMapper.update(updateWrapper);

        //更新失败直接抛出异常
        //只有抛出异常 事务才会回滚
        if(update<=0){
            throw new RuntimeException("博客点赞数更新失败");
        }
        return true;
    }
}
