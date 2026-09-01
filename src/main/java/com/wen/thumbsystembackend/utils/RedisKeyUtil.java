package com.wen.thumbsystembackend.utils;

import com.wen.thumbsystembackend.constant.ThumbConstant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.datetime.standard.DateTimeFormatterFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class RedisKeyUtil {

    //这个date大概长 12:21 时:分
    public static String getTempThumbKey(String date) {
        if(date==null){
            return null;
        }
        return ThumbConstant.TEMPT_THUMB_KEY_PREFIX + date;
    }
}
