package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;

/**
 * 缓存项权重器接口
 *
 * @author Charles Fry
 * @since 11.0
 */
@GwtCompatible
@FunctionalInterface
@ElementTypesAreNonnullByDefault
public interface Weigher<K, V> {

  /** 计算权重 */
  int weigh(K key, V value);

}
