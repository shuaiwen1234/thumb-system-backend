package com.wen.thumbsystembackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.common.redis.RedisBlog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.entity.vo.BlogVO;
import com.wen.thumbsystembackend.manager.cache.CacheManager;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.BlogService;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service(value = "blogServiceRedis")
public class BlogServiceRedisImpl extends ServiceImpl<BlogMapper,Blog> implements BlogService {
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CacheManager cacheManager;
    @Qualifier("thumbServiceMQ")
    @Autowired
    private ThumbService thumbService;

    private final ThreadPoolExecutor  THREAD_POOL = new ThreadPoolExecutor(8,16,1,TimeUnit.MINUTES,new LinkedBlockingQueue<>(100),new ThreadPoolExecutor.AbortPolicy());

    @Override
    public BaseResponse<BlogVO> getBlogVO(Long blogId) {
        User loginUser = UserContext.getUser();
        Long userId = loginUser == null ? null : loginUser.getUserId();

        //三级缓存拿博客本体（不含 hasThumb）
        Blog blog = loadBlog(blogId);

        //组装 VO + 判断是否点赞（按用户单独算，不进缓存）
        BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
        if (userId == null) {
            blogVO.setHasThumb(false);
        } else {
            blogVO.setHasThumb(thumbService.hasThumb(userId, blogId));
        }
        return ResultUtils.success(blogVO);
    }

    /**
     * 走三级缓存拿博客本体（不含 hasThumb，按用户单独算）
     * 本地 -> Redis -> DB；只有 DB 回源时才异步写回 Redis
     * 还负责重建缓存
     */
    private Blog loadBlog(Long blogId) {
        String fid = blogId.toString();

        //1. 本地缓存
        Blog blog = (Blog) cacheManager.getBlog(fid);

        //2. Redis（独立 key：blog:{id} / blog:thumb:count:{id}）
        if (blog == null) {
            RedisBlog rb = (RedisBlog) redisTemplate.opsForValue().get(BlogConstant.BLOG_KEY_PREFIX + fid);
            Object countObj = redisTemplate.opsForValue().get(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + fid);
            Long count = countObj == null ? null : Long.valueOf(countObj.toString());
            if (rb != null && count != null) {
                blog = BeanUtil.copyProperties(rb, Blog.class);
                blog.setThumbCount(count);
            }
        }

        //3. DB 回源
        boolean fromDb = false;
        if (blog == null) {
            blog = blogMapper.selectById(blogId);
            fromDb = true;
        }
        if (blog == null) {
            throw new RuntimeException("该博客走丢了 请稍后再试");
        }

        //4. 只有 DB 回源才异步写回 Redis（缓存命中无需重复写）
        if (fromDb) {
            RedisBlog rb = BeanUtil.copyProperties(blog, RedisBlog.class);
            Long count = blog.getThumbCount();
            try {
                THREAD_POOL.execute(() -> {
                    redisTemplate.opsForValue().set(BlogConstant.BLOG_KEY_PREFIX + fid, rb, 30, TimeUnit.DAYS);
                    redisTemplate.opsForValue().set(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + fid, count, 30, TimeUnit.MINUTES);
                });
            } catch (RejectedExecutionException e) {
                //池满静默丢弃：写不回算了，下次查询再重建，绝不让异步任务反噬请求
            }
        }

        //5. 记热度
        cacheManager.recordBlogHit(fid, blog);
        return blog;
    }

    /**
     * 查询博客列表
     * @return
     */
    @Override
    public BaseResponse<List<BlogVO>> getBlogVOList() {
        User loginUser = UserContext.getUser();
        Long userId = loginUser == null ? null : loginUser.getUserId();

        // 1. 只查所有博客 id
        List<Long> blogIds = blogMapper.selectList(
                new LambdaQueryWrapper<Blog>().select(Blog::getId).orderByAsc(Blog::getCreateTime)
        ).stream().map(Blog::getId).collect(Collectors.toList());

        if (blogIds.isEmpty()) {
            return ResultUtils.success(new ArrayList<>());
        }

        // 2. 该用户点赞集合（未登录不查）
        List<Long> thumbedIds = userId == null
                ? new ArrayList<>()
                : thumbService.thumbedList(userId);

        // 3. 一次批量从 Redis 取博客本体 + 点赞数（独立 key，2 次 mget）
        List<String> fullBlogKeys = blogIds.stream().map(id -> BlogConstant.BLOG_KEY_PREFIX + id).collect(Collectors.toList());
        List<String> fullCountKeys = blogIds.stream().map(id -> BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + id).collect(Collectors.toList());
        List<Object> blogObjs = redisTemplate.opsForValue().multiGet(fullBlogKeys);
        List<Object> countObjs = redisTemplate.opsForValue().multiGet(fullCountKeys);

        // 4. 命中进结果，miss 的 id 收集起来
        Map<Long, Blog> idToBlog = new LinkedHashMap<>();
        List<Long> missIds = new ArrayList<>();
        for (int i = 0; i < blogIds.size(); i++) {
            Long id = blogIds.get(i);
            Blog cached = (Blog) cacheManager.getBlog(id.toString()); // 先看本地缓存
            if (cached != null) {
                idToBlog.put(id, cached);
                continue;
            }
            RedisBlog rb = (RedisBlog) blogObjs.get(i);
            Object countObj = countObjs.get(i);
            Long count = countObj == null ? null : Long.valueOf(countObj.toString());
            if (rb != null && count != null) {
                Blog b = BeanUtil.copyProperties(rb, Blog.class);
                b.setThumbCount(count);
                idToBlog.put(id, b);
            } else {
                missIds.add(id);
            }
        }

        // 5. miss 的一次性 IN 查回（N 次 selectById → 1 次）
        if (!missIds.isEmpty()) {
            List<Blog> missBlogs = blogMapper.selectBatchIds(missIds);
            for (Blog b : missBlogs) {
                Long id = b.getId();
                idToBlog.put(id, b);

                //因为这些数据既不在本地缓存也不在redis缓存里 重建缓存
                try {
                    THREAD_POOL.execute(() -> {
                        redisTemplate.opsForValue().set(BlogConstant.BLOG_KEY_PREFIX + id, BeanUtil.copyProperties(b, RedisBlog.class), 30, TimeUnit.DAYS);
                        redisTemplate.opsForValue().set(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + id, b.getThumbCount(), 30, TimeUnit.MINUTES);
                    });
                } catch (RejectedExecutionException e) {
                    //池满静默丢弃：写不回算了，下次查询再重建，绝不让异步任务反噬请求
                }
            }
        }

        // 6. 按 id 顺序组装 VO（LinkedHashMap 保序）
        List<BlogVO> vos = new ArrayList<>();
        for (Long id : blogIds) {
            Blog blog = idToBlog.get(id);
            if (blog == null) {
                continue;
            }
            BlogVO vo = BeanUtil.copyProperties(blog, BlogVO.class);
            vo.setHasThumb(userId != null && thumbedIds.contains(id));
            vos.add(vo);
            // 写回 + 记热度（复用 loadBlog 的写回逻辑）
            cacheManager.recordBlogHit(id.toString(), blog);
        }
        return ResultUtils.success(vos);
    }
    @Override
    public Long createBlog(BlogVO blogVO) {
        User user = UserContext.getUser();

        Long userId = user==null?null:user.getUserId();
        if(userId == null){
            throw new RuntimeException("请先登录再进行该操作");
        }
        Blog blog = BeanUtil.copyProperties(blogVO, Blog.class);
        blog.setUserId(userId);
        blog.setCreateTime(LocalDateTime.now());
        blog.setUpdateTime(LocalDateTime.now());
        blog.setThumbCount(0L);
        blogMapper.insert(blog);
        //新发布的博客会在redis中保存一个月
        RedisBlog redisBlog = BeanUtil.copyProperties(blog, RedisBlog.class);
        redisTemplate.opsForValue().set(BlogConstant.BLOG_KEY_PREFIX + blog.getId(), redisBlog, 30, TimeUnit.DAYS);
        //构造他的点赞计数器
        redisTemplate.opsForValue().set(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + blog.getId(), 0L, 30, TimeUnit.MINUTES);
        return blog.getId();

    }

    @Override
    public BaseResponse<List<BlogVO>> getMineBlogVOList(Long pageNum, Long pageSize) {
        Page<Blog> page = new Page(pageNum,pageSize);
        Long userId = UserContext.getUser()==null?null:UserContext.getUser().getUserId();
        if(userId == null){
            return ResultUtils.success(new  ArrayList<>());
        }
        //获取用户的博客
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>();
        wrapper.eq(Blog::getUserId, userId);
        wrapper.orderByDesc(Blog::getUpdateTime);
        Page<Blog> blogPage = blogMapper.selectPage(page, wrapper);
        List<Blog> records = blogPage.getRecords();

        //查询出这个用户所有的点过赞的博客
        List<Long> blogIds = thumbService.thumbedList(userId);
        //转换为blogVO
        List<BlogVO> vos = new ArrayList<>();
        for(Blog blog : records){
            BlogVO vo = new BlogVO();
            BeanUtils.copyProperties(blog,vo);

            //判断是否点赞
            if (blogIds.contains(vo.getId())) {
                vo.setHasThumb(true);
                vos.add(vo);
            } else {
                vo.setHasThumb(false);
                vos.add(vo);
            }
            // 写回 + 记热度（复用 loadBlog 的写回逻辑）
            cacheManager.recordBlogHit(blog.getId().toString(), blog);
        }


        return ResultUtils.success(vos);

    }

    @Override
    public BaseResponse<List<BlogVO>> getMineThumbBlogVOList(Long pageNum, Long pageSize) {
        Page<Thumb> page = new Page(pageNum,pageSize);
        Long userId = UserContext.getUser()==null?null:UserContext.getUser().getUserId();
        if(userId == null){
            return ResultUtils.success(new  ArrayList<>());
        }

        //查询出用户点过赞的博客的id
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<Thumb>().eq(Thumb::getUserId, userId);
        wrapper.orderByDesc(Thumb::getCreateTime);
        wrapper.select(Thumb::getBlogId);
        Page<Thumb> selectPage = thumbMapper.selectPage(page, wrapper);
        List<Thumb> records = selectPage.getRecords();
        List<Long> blogIds = records.stream().map(Thumb::getBlogId).collect(Collectors.toList());

        if (blogIds.isEmpty()) {
            // 没有点赞 / 页码越界 → 直接返回空列表
            return ResultUtils.success(new ArrayList<>());
        }

        //根据这些博客id查询对应的博客
        List<Blog> blogs = blogMapper.selectList(new LambdaQueryWrapper<Blog>().in(Blog::getId, blogIds));
        //转换为对应的VO
        List<BlogVO> blogVOs = blogs.stream().map(blog ->{
            BlogVO vo = new BlogVO();
            BeanUtils.copyProperties(blog,vo);
            vo.setHasThumb(true);
            // 写回 + 记热度（复用 loadBlog 的写回逻辑）
            cacheManager.recordBlogHit(blog.getId().toString(), blog);
            return vo;
        }).collect(Collectors.toList());

        return ResultUtils.success(blogVOs);
    }

    @Override
    public BaseResponse<Boolean> updateBlog(BlogVO blogVO) {

        Object UserId = UserContext.getUser()==null?null:UserContext.getUser().getUserId();
        if(UserId == null){
            throw new RuntimeException("请先登录再进行此操作");
        }
        Long userId = Long.valueOf(UserId.toString());

        //先判断当前登录的用户是否为这篇博客的作者
        //1. 先查询出这个博客对应的作者
        Blog blog = null;
        Long blogId = blogVO.getId();
        //1.1 先尝试从本地缓存拿userId
        blog = (Blog)cacheManager.getBlog(blogId.toString());
        if(blog == null){
            //1.2 再尝试从redis里拿userId
            RedisBlog object = (RedisBlog) redisTemplate.opsForValue().get(BlogConstant.BLOG_KEY_PREFIX + blogId);
            blog = BeanUtil.copyProperties(object,Blog.class);
        }
        if(blog == null){
            //最后去数据库查
            blog = blogMapper.selectById(blogId);
        }
        if(blog == null){
            throw new RuntimeException("该博客暂时走丢了 请稍后再试");
        }

        //2. 判断当前要修改博客的这个用户是否为作者
        if(!blog.getUserId().equals(UserId)){
            //3. 不是 直接拒绝
            throw new RuntimeException("操作失败 只有该博客的作者才能进行修改");
        }

        //4. 更新博客 同步更新本地缓存 redis 数据库
        //开启只有非null的字段才复制
        BeanUtil.copyProperties(blogVO,blog,CopyOptions.create().setIgnoreNullValue(true));
        cacheManager.putBlogIfPresent(blogId.toString(), blog);
        //先改数据库再删除缓存
        blogMapper.updateById(blog);
        redisTemplate.delete(BlogConstant.BLOG_KEY_PREFIX + blogId);
        //5. 返回
        return ResultUtils.success(true);

    }

    @Override
    @Transactional
    public BaseResponse<Boolean> deleteBlog(Long blogId) {

        Object UserId = UserContext.getUser()==null?null:UserContext.getUser().getUserId();
        if(UserId == null){
            throw new RuntimeException("请先登录再进行此操作");
        }
        Long userId = Long.valueOf(UserId.toString());

        //先判断当前登录的用户是否为这篇博客的作者
        //1. 先查询出这个博客对应的作者
        Blog blog = null;
        //1.1 先尝试从本地缓存拿userId
        blog = (Blog)cacheManager.getBlog(blogId.toString());
        if(blog == null){
            //1.2 再尝试从redis里拿userId
            RedisBlog object = (RedisBlog) redisTemplate.opsForValue().get(BlogConstant.BLOG_KEY_PREFIX + blogId);
            blog = BeanUtil.copyProperties(object,Blog.class);
        }
        if(blog == null){
            //最后去数据库查
            blog = blogMapper.selectById(blogId);
        }
        if(blog == null){
            throw new RuntimeException("该博客暂时走丢了 请稍后再试");
        }

        //2. 判断当前要修改博客的这个用户是否为作者
        if(!blog.getUserId().equals(UserId)){
            //3. 不是 直接拒绝
            throw new RuntimeException("操作失败 只有该博客的作者才能进行删除");
        }

        //4. 删除本地缓存(点赞记录缓存＋博客缓存) 删除redis缓存 删除mysql
        //4.1 查出该博客被哪些用户点过赞(用于清 Redis)
        List<Long> userIds = thumbMapper.selectList(
                        new LambdaQueryWrapper<Thumb>()
                                .select(Thumb::getUserId)
                                .eq(Thumb::getBlogId, blogId))
                .stream().map(Thumb::getUserId).collect(Collectors.toList());

        //5. 删数据库的博客
        blogMapper.deleteById(blogId);
        //删数据库该博客对应的点赞记录
        thumbMapper.delete(new LambdaQueryWrapper<Thumb>().eq(Thumb::getBlogId, blogId));


        //这个东西的作用大概是等方法执行到这里时就会先跳过这个继续执行 登记一个回调 等方法结束事务提交了由事务框架在同一个线程里按顺序调用
        //5.1 跨系统的副作用(Redis / 本地缓存)必须等事务提交后再执行:
        //提交前删,并发读因"快照读看不到未提交的删除"会把这篇文章重新写回缓存
        //因为如果要是不这样的话 先删数据库然后直接删缓存的话等到缓存删完但是方法没结束 事务没提交 快照读读到旧数据之后就会回填到redis
        //导致redis里缓存了旧的数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //删 redis 的点赞记录
                for (Long uid : userIds) {
                    redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX + uid, blogId.toString());
                }
                //删 redis 缓存的博客 + 点赞数缓存
                redisTemplate.delete(BlogConstant.BLOG_KEY_PREFIX + blogId);
                redisTemplate.delete(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + blogId);
                //清本地缓存条目(点赞记录和博客)
                cacheManager.deleteIfPresent(blogId.toString(), userIds);
            }
        });

        //6. 返回
        return ResultUtils.success(true);
    }
}
