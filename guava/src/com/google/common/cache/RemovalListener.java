package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;

/**
 * 缓存项移除监听器
 *
 * @author Charles Fry
 * @since 10.0
 */
@GwtCompatible
@FunctionalInterface
@ElementTypesAreNonnullByDefault
public interface RemovalListener<K, V> {

  /** 处理缓存项移除通知 */
  void onRemoval(RemovalNotification<K, V> notification);

}
