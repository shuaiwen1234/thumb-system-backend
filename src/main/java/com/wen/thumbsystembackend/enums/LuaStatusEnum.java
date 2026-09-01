package com.wen.thumbsystembackend.enums;

import lombok.Getter;

@Getter
public enum LuaStatusEnum {

    SUCCESS(1L),  // 操作成功
    FAIL(-1L);  //操作失败


    private Long value;
    LuaStatusEnum(Long value) {
        this.value = value;
    }
}
