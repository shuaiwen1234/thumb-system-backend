package com.wen.thumbsystembackend.enums;

import lombok.Getter;

@Getter
public enum ThumbTypeEnum {

    INCR(1),  //点赞
    DECR(-1),  //取消点赞
    NONE(0);  //不做操作


    private int type;

    ThumbTypeEnum(int type) {
        this.type = type;
    }
}
