package com.wen.thumbsystembackend.manager.cache;

import lombok.Data;

// 新增返回结果类

/**
 * @author zhangziwen
 * 回报：add 之后告诉你什么
 */
@Data
public class AddResult {
    // 被挤出的 key
    private final String expelledKey;
    // 当前 key 是否进入 TopK
    private final boolean isHotKey;
    // 当前操作的 key
    private final String currentKey;

    public AddResult(String expelledKey, boolean isHotKey, String currentKey) {
        this.expelledKey = expelledKey;
        this.isHotKey = isHotKey;
        this.currentKey = currentKey;
    }
}