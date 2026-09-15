package com.wen.thumbsystembackend.task;

import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.manager.cache.CacheManager;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库的补偿措施  
 * @author zhangziwen
 */  
@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {  
  
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private BlogMapper blogMapper;

    @Scheduled(cron = "0 0 2 * * *")
    public void clearDrifted() {
        log.info("开始补偿数据");
        //找出所有的漂移了的博客的id(点赞数和点赞表里的行数不一致)
        List<Long> driftedIds = blogMapper.findDriftedBlogIds();
        if(!driftedIds.isEmpty()){
            //每次只处理500条 防止in里的数据过多
            for(int i =0;i<driftedIds.size();i+=500){
                int last = Math.min(i+500,driftedIds.size());
                List<Long> ids = driftedIds.subList(i, last);
                blogMapper.recountThumbCount(ids);
            }

            //清除缓存
            driftedIds.forEach(id -> redisTemplate.delete(
                    BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + id));
        }
    }


}
