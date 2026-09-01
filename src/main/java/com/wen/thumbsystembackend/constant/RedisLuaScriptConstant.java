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
                    "    return 1  -- 返回 1 表示成功",Long.class
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
                    "    return 1  -- 返回 1 表示成功",Long.class
    );
}
