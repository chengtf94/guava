package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * KV缓存容器接口（自动加载模式）：支持自动加载值
 *
 * @author Charles Fry
 * @since 11.0
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
public interface LoadingCache<K, V> extends Cache<K, V>, Function<K, V> {

  /** 获取缓存项：若缓存中不存在，则调用预先设置的loader自动加载（if cached, return; otherwise create, cache and return） */
  @CanIgnoreReturnValue
  V get(K key) throws ExecutionException;

  /** 获取缓存项：若缓存中不存在，则调用loader自动加载，与get的区别是调用方不用catch显式异常ExecutionException */
  @CanIgnoreReturnValue
  V getUnchecked(K key);

  /** 批量获取缓存项 */
  @CanIgnoreReturnValue // TODO(b/27479612): consider removing this
  ImmutableMap<K, V> getAll(Iterable<? extends K> keys) throws ExecutionException;

  /** 刷新缓存项：即异步自动加载，以异步任务执行自动加载刷新 */
  void refresh(K key);

  /** 获取线程安全的缓存项视图Map */
  @Override
  ConcurrentMap<K, V> asMap();

  @Deprecated
  @Override
  V apply(K key);

}
