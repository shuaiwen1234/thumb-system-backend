package com.wen.thumbsystembackend.listener.thumb;

import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.MqConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 监听点赞/取消点赞消息（合并队列，按事件类型分流处理）
 */
@Component
@Slf4j
public class ThumbMessageListener {
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstant.THUMB_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = MqConstant.THUMB_EXCHANGE_NAME, type = "direct", durable = "true"),
            key = MqConstant.THUMB_BINDING_KEY
    ), containerFactory = "batchQueueTaskListenerContainerFactory")
    public void onThumbMessages(List<ThumbEvent> thumbEventList) {
        Map<String, ThumbEvent> lastEventMap = new LinkedHashMap<>();
        //把thumbEvent保存在map里 根据用户的id以及博客的id作为键 实现先发生的操作覆盖后发生的操作
        for(ThumbEvent thumbEvent : thumbEventList) {
            lastEventMap.put(thumbEvent.getUserId()+":"+thumbEvent.getBlogId(), thumbEvent);
        }
        //获取点赞事件列表
        List<ThumbEvent> incrList = lastEventMap.values().stream().filter(event-> {
            return event.getType().equals(ThumbEvent.EventType.INCR);
        }).collect(Collectors.toList());

        //获取取消点赞事件列表
        List<ThumbEvent> decrList = lastEventMap.values().stream().filter(event-> {
            return event.getType().equals(ThumbEvent.EventType.DECR);
        }).collect(Collectors.toList());

        // 更新点赞记录表
        if (!incrList.isEmpty()) {
            thumbMapper.insertIgnoreThumbEventList(incrList);
        }
        if (!decrList.isEmpty()) {
            thumbMapper.deleteByThumbEventList(decrList);
        }

        // 计算每个博客的净变化量：点赞 +1，取消 -1
        Map<Long, Long> deltaMap = new HashMap<>();
        thumbEventList.forEach(e -> deltaMap.merge(e.getBlogId(),
                e.getType() == ThumbEvent.EventType.INCR ? 1L : -1L, Long::sum));
        deltaMap.entrySet().removeIf(entry -> entry.getValue() == 0L);

        List<Blog> blogs= new ArrayList<>(deltaMap.isEmpty()?0:deltaMap.size());
        // 更新 DB 点赞数
        if (!deltaMap.isEmpty()) {
            List<Long> idList = deltaMap.keySet().stream().toList();
            //每次只处理500条 防止in里的数据量过大
            for(int i=0;i<idList.size();i+=500){
                int last = Math.min(i+500,idList.size());
                List<Long> fiveHundredIds = idList.subList(i, last);
                blogMapper.recountThumbCount(fiveHundredIds);
            }

            //去数据库里查询出最新的数据
             blogs = blogMapper.getBlogIdAndThumbCount(deltaMap.keySet().stream().toList());
        }



        // 更新 Redis 缓存的点赞数（独立 key：blog:thumb:count:{blogId}，TTL 30 分钟与 createBlog 一致）
        for (Blog blog : blogs) {
            Long blogId = blog.getId();
            Long newCount = blog.getThumbCount();
            String countKey = BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX + blogId;
            Object originObj = redisTemplate.opsForValue().get(countKey);
            //缓存没过期时重建缓存
            if(originObj!=null){
                redisTemplate.opsForValue().set(countKey, newCount, 30, TimeUnit.MINUTES);
            }
        }
    }
}