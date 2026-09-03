package com.wen.thumbsystembackend.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author zhangziwen
 * 整车：把零件装进多级缓存流程
 */
@Component
@Slf4j
public class CacheManager {

    private TopK hotKeyDetector;
    private Cache<String, Object> localCache;

    @Bean
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


    public String buildCacheKey(String hashKey,String key) {
        return hashKey + ":" + key;
    }

    /**
     *
     * @param hashKey thumb:1(用户id)
     * @param key 1(博客id)
     * @return
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    public Object get(String hashKey,String key) {
        //获取唯一的key
        String onlyKey = buildCacheKey(hashKey, key);
        //先在本地尝试获取
        Object ifPresent = localCache.getIfPresent(onlyKey);
        if (ifPresent != null) {
            //记录访问次数(每次访问时次数加一)
            hotKeyDetector.add(key,1);
            return ifPresent;
        }
        //没查到就从redis里的点赞记录里查
        //因为redis里的对应的数据不会删除 查不到就代表用户没点过赞
        Object object = redisTemplate.opsForHash().get(hashKey, key);
        if(object == null) {
           return null;
        }
        //点赞了 将访问次数加一
        AddResult result = hotKeyDetector.add(key, 1);
        //如果是HotKey且不在本地缓存中 就把他加到本地缓存中
        if(result.isHotKey()){
            localCache.put(onlyKey, object);
        }
        return  object;

    }

    //可以用于实现缓存一致型 比如redis更新后(点赞或者取消点赞)调用可以调用这个方法更新本地缓存里的数据
    public void putIfPresent(String hashKey,String key,Object value) {
        String onlyKey = buildCacheKey(hashKey, key);
        if(localCache.getIfPresent(onlyKey) == null) {
            //本地缓存中没有这个数据 不是超级热点
            return;
        }
        localCache.put(onlyKey, value);
    }
    //定时清理HotKey 让热度定期衰减
    @Scheduled(fixedRate = 20,timeUnit=TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }
}
