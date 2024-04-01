package com.google.common.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许并发处理事件注解：用于标记事件订阅者方法是线程安全的，允许多个线程同时执行该订阅者方法处理事件
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ElementTypesAreNonnullByDefault
public @interface AllowConcurrentEvents {}
