package com.wen.thumbsystembackend.listener.thumb;

import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.MqConstant;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstant.THUMB_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = MqConstant.THUMB_EXCHANGE_NAME, type = "direct", durable = "true"),
            key = MqConstant.THUMB_BINDING_KEY
    ), containerFactory = "batchQueueTaskListenerContainerFactory")
    public void onThumbMessages(List<ThumbEvent> thumbEventList) {
        // 按事件类型分流：点赞(INCR) 与 取消点赞(DECR)
        List<ThumbEvent> incrList = thumbEventList.stream()
                .filter(e -> e.getType() == ThumbEvent.EventType.INCR)
                .collect(Collectors.toList());
        List<ThumbEvent> decrList = thumbEventList.stream()
                .filter(e -> e.getType() == ThumbEvent.EventType.DECR)
                .collect(Collectors.toList());

        // 顺序关键：先 insert 点赞记录，再 delete 取消记录（保证"赞后取消"最终无记录）
        if (!incrList.isEmpty()) {
            thumbMapper.insertIgnoreThumbEventList(incrList);
        }
        if (!decrList.isEmpty()) {
            thumbMapper.deleteByThumbEventList(decrList);
        }

        // 计算每个博客的净变化量：点赞 +1，取消 -1
        Map<Long, Long> deltaMap = new HashMap<>();
        incrList.forEach(e -> deltaMap.merge(e.getBlogId(), 1L, Long::sum));
        decrList.forEach(e -> deltaMap.merge(e.getBlogId(), -1L, Long::sum));
        deltaMap.entrySet().removeIf(entry -> entry.getValue() == 0L);

        // 更新 DB 点赞数
        if (!deltaMap.isEmpty()) {
            blogMapper.addThumbCountByMap(deltaMap);
        }

        // 更新 Redis 缓存的点赞数
        for (Map.Entry<Long, Long> entry : deltaMap.entrySet()) {
            Long blogId = entry.getKey();
            Long delta = entry.getValue();
            Object originObj = redisTemplate.opsForHash().get(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX, blogId.toString());
            Long origin = originObj == null ? null : Long.valueOf(originObj.toString());
            long newCount = (origin == null ? 0L : origin) + delta;
            redisTemplate.opsForHash().put(BlogConstant.BLOG_THUMB_COUNT_KEY_PREFIX, blogId.toString(), newCount);
        }
    }
}