package com.wen.thumbsystembackend.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author zhangziwen
 * 整车：把零件装进多级缓存流程
 */
@Component
@Slf4j
public class CacheManager {

    private TopK hotKeyDetector;
    //存点赞记录
    private Cache<String, Object> localCache;
    //存热门博客
    private Cache<String, Object> localBlogCache;

    @Bean
    //这里是借 @Bean 的副作用来初始化字段
    public TopK getHotKeyDetector() {
        hotKeyDetector = new HeavyKeeper(
                // 监控 Top 100 Key
                100,
                // 宽度
                100000,
                // 深度
                5,
                // 衰减系数
                0.92,
                // 最小出现 10 次才记录
                10
        );
        return hotKeyDetector;
    }

    @Bean
    public Cache<String, Object> localCache() {
        localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        return localCache;
    }

    @Bean
    public Cache<String, Object> localBlogCache() {
        localBlogCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();
        return localBlogCache;
    }


    public String buildCacheKey(String hashKey,String key) {
        return hashKey + ":" + key;
    }


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    /**
     *从本地缓存尝试取数据
     * @param hashKey thumb:1(用户id)
     * @param key 1(博客id)
     * @return
     */
    public Object get(String hashKey, String key) {
            return localCache.getIfPresent(buildCacheKey(hashKey, key));
        }


    /**
     * 增加缓存的热度
     * @param hashKey thumb:1(用户id)
     * @param key 1(博客id)
     * @param value 0或者1(点赞或者取消点赞)
     */
    public void recordThumbHit(String hashKey, String key, Object value) {
        AddResult result = hotKeyDetector.add(key, 1);
        if (result.isHotKey()) {
            localCache.put(buildCacheKey(hashKey, key), value);
        }
    }

    /**
     *
     * @param blogId 1(博客id)
     * @param value 博客VO
     */
    public void recordBlogHit(String blogId, Object value) {
        AddResult result = hotKeyDetector.add(blogId, 1);
        if (result.isHotKey()) {
            localBlogCache.put(blogId, value);
        }
    }



    /**
     * 获取博客
     * @param blogId
     * @return
     */
    public Object getBlog(String blogId) {
        return  localBlogCache.getIfPresent(blogId);
    }

    //可以用于实现点赞记录的缓存一致型 比如redis更新后(点赞或者取消点赞)调用可以调用这个方法更新本地缓存里的数据
    public void putIfPresent(String hashKey,String key,Object value) {
        String onlyKey = buildCacheKey(hashKey, key);
        if(localCache.getIfPresent(onlyKey) == null) {
            //本地缓存中没有这个数据 不是超级热点
            return;
        }
        localCache.put(onlyKey, value);
    }

    //可以用于实现博客的缓存一致型 比如redis更新后(点赞或者取消点赞)调用可以调用这个方法更新本地缓存里的数据
    public void putBlogIfPresent(String blogId,Object value) {
        if(localBlogCache.getIfPresent(blogId) == null) {
            //本地缓存中没有这个数据 不是超级热点
            return;
        }
        localBlogCache.put(blogId, value);
    }

    //定时清理HotKey 让热度定期衰减
    @Scheduled(fixedRate = 20,timeUnit=TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }

    /**
     * 用于删除博客之后清除对应的缓存
     * @param key 博客id
     * @param userIds 存放给这个博客点赞的用户id的集合
     */
    public void deleteIfPresent(String key, List<Long> userIds) {
        for(Long userId : userIds){
            String onlyKey = buildCacheKey(ThumbConstant.USER_THUMB_KEY_PREFIX+userId, key);
            localCache.invalidate(onlyKey);
        }
        localBlogCache.invalidate(key);
    }
}
