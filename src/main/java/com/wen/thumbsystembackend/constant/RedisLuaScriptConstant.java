package com.wen.thumbsystembackend.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisLuaScriptConstant {

    //这里的long指的是这段lua脚本的返回值类型是Long
    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>(
            "local tempThumbKey = KEYS[1]       -- 临时计数键(如 thumb:temp:{timeSlice})\n" +
                    "    local userThumbKey = KEYS[2]       -- 用户点赞状态键(如 thumb:{userId})\n" +
                    "    local userId = ARGV[1]              -- 用户 ID\n" +
                    "    local blogId = ARGV[2]              -- 博客 ID\n" +
                    "    \n" +
                    "    -- 1. 检查是否已点赞（避免重复操作）\n" +
                    "    if redis.call('HEXISTS', userThumbKey, blogId) == 1 then\n" +
                    "        return -1  -- 已点赞，返回 -1 表示失败\n" +
                    "    end\n" +
                    "    \n" +
                    "    -- 2. 获取旧值(不存在则默认为 0)\n" +
                    "    local hashKey = userId .. ':' .. blogId\n" +
                    "    local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)\n" +
                    "    \n" +
                    "    -- 3. 计算新值\n" +
                    "    local newNumber = oldNumber + 1\n" +
                    "    \n" +
                    "    -- 4. 原子性更新：写入临时计数 + 标记用户已点赞\n" +
                    "    redis.call('HSET', tempThumbKey, hashKey, newNumber)\n" +
                    "    redis.call('HSET', userThumbKey, blogId, 1)\n" +
                    "    \n" +
                    "    return 1  -- 返回 1 表示成功", Long.class
    );


    public static final RedisScript<Long> UNTHUMB_SCRIPT = new DefaultRedisScript<>(
            "local tempThumbKey = KEYS[1]       -- 临时计数键(如 thumb:temp:{timeSlice})\n" +
                    "    local userThumbKey = KEYS[2]       -- 用户点赞状态键(如 thumb:{userId})\n" +
                    "    local userId = ARGV[1]              -- 用户 ID\n" +
                    "    local blogId = ARGV[2]              -- 博客 ID\n" +
                    "\n" +
                    "    -- 1. 检查用户是否已点赞（若未点赞，直接返回失败）\n" +
                    "    if redis.call('HEXISTS', userThumbKey, blogId) ~= 1 then\n" +
                    "        return -1  -- 未点赞，返回 -1 表示失败\n" +
                    "    end\n" +
                    "\n" +
                    "    -- 2. 获取当前临时计数(若不存在则默认为 0)\n" +
                    "    local hashKey = userId .. ':' .. blogId\n" +
                    "    local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)\n" +
                    "\n" +
                    "    -- 3. 计算新值并更新\n" +
                    "    local newNumber = oldNumber - 1\n" +
                    "\n" +
                    "    -- 4. 原子性操作：更新临时计数 + 删除用户点赞标记\n" +
                    "    redis.call('HSET', tempThumbKey, hashKey, newNumber)\n" +
                    "    redis.call('HDEL', userThumbKey, blogId)\n" +
                    "\n" +
                    "    return 1  -- 返回 1 表示成功", Long.class
    );

    public static final RedisScript<Long> THUMB_SCRIPT_MQ = new DefaultRedisScript<>(" local userThumbKey = KEYS[1]  \n" +
            " local blogId = ARGV[1]  \n" +
            "local exists = redis.call(\"HEXISTS\", KEYS[1], ARGV[1])  \n" +
            "if exists == 1 then  \n" +
            "   local v = tonumber(redis.call(\"HGET\", KEYS[1], ARGV[1]) or 0)  \n" +
            "   if v == 1 then return -1 end  -- 值=1 真点过 -> 拒绝\n" +
            "   redis.call(\"HSET\", KEYS[1], ARGV[1], 1)  \n" +
            "   redis.call(\"EXPIRE\", KEYS[1], 864000)  \n" +
            "   return 1  -- 值=0 明确未赞 -> 直接点赞\n" +
            "end  \n" +
            "redis.call(\"HSET\", KEYS[1], ARGV[1], 1)  \n" +
            "redis.call(\"EXPIRE\", KEYS[1], 864000)  \n" +
            "return 2  -- Redis 无记录(可能过期) -> 交给 DB 兜底", Long.class);

    public static final RedisScript<Long> UN_THUMB_SCRIPT_MQ = new DefaultRedisScript<>("local userThumbKey = KEYS[1]  \n" +
            "local blogId = ARGV[1]  \n" +
            "local exists = redis.call(\"HEXISTS\", KEYS[1], ARGV[1])  \n" +
            "if exists == 1 then  \n" +
            "   local v = tonumber(redis.call(\"HGET\", KEYS[1], ARGV[1]) or -1)  \n" +
            "   if v == 0 then return -1 end  -- 值=0 未赞 -> 拒绝取消\n" +
            "   redis.call(\"HSET\", KEYS[1], ARGV[1], 0)  \n" +
            "   redis.call(\"EXPIRE\", KEYS[1], 864000)  \n" +
            "   return 1  -- 值=1 明确已赞 -> 直接取消\n" +
            "end  \n" +
            "redis.call(\"HSET\", KEYS[1], ARGV[1], 0)  \n" +
            "redis.call(\"EXPIRE\", KEYS[1], 864000)  \n" +
            "return 2  -- Redis 无记录(可能过期) -> 交给 DB 兜底", Long.class);

}
