package com.wen.thumbsystembackend.manager.cache;

/**
 * author zhangziwen
 * @param key
 * @param count
 * 这个record是java16的一个新语法
 * 这里这么写就相当于定义了一个带有(private final)key和(private final)count变量的类并提供对应的全参构造 get toString hashCode equals方法
 * 数据盒 记录一个热点数据长啥样
 */
public record Item(String key,int count) {

}
