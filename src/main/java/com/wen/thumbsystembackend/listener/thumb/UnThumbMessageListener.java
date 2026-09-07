package com.wen.thumbsystembackend.listener.thumb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wen.thumbsystembackend.constant.MqConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监听取消点赞信息
 */
@Component
public class UnThumbMessageListener {
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private ThumbMapper thumbMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstant.THUMB_UNDOTHUMB_QUEUE_NAME,durable = "true"),
            exchange = @Exchange(name = MqConstant.THUMB_EXCHANGE_NAME,type = "direct",durable = "true"),
            key = MqConstant.THUMB_UNDOTHUMB_BINDINGKEY
    ),containerFactory = "batchQueueTaskListenerContainerFactory")
    public void onUnThumbMessages(List<ThumbEvent> thumbEventList) {
        thumbMapper.deleteByThumbEventList(thumbEventList);
        Map<Long, Long> map = thumbEventList.stream().collect(Collectors.groupingBy(thumb -> thumb.getBlogId(),
                Collectors.summingLong(thumb -> -1L)));   // 每个消息贡献 -1));

        //更新博客的点赞数
        blogMapper.addThumbCountByMap(map);
    }
}
