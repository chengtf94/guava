package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CompatibleWith;
import com.google.errorprone.annotations.DoNotMock;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import javax.annotation.CheckForNull;

/**
 * KV缓存容器接口
 *
 * @author Charles Fry
 * @since 10.0
 */
@DoNotMock("Use CacheBuilder.newBuilder().build()")
@GwtCompatible
@ElementTypesAreNonnullByDefault
public interface Cache<K, V> {

  /** 添加缓存项：单个或批量，若缓存中已存在，则替换旧值 */
  void put(K key, V value);
  void putAll(Map<? extends K, ? extends V> m);

  /** 获取缓存项：若缓存中不存在，则返回null */
  @CheckForNull
  @CanIgnoreReturnValue
  V getIfPresent(@CompatibleWith("K") Object key);

  /** 获取缓存项：若缓存中不存在，则调用loader自动加载（if cached, return; otherwise create, cache and return） */
  @CanIgnoreReturnValue // TODO(b/27479612): consider removing this
  V get(K key, Callable<? extends V> loader) throws ExecutionException;

  /** 批量获取缓存项：只返回缓存中存在的 */
  ImmutableMap<K, V> getAllPresent(Iterable<? extends Object> keys);

  /** 获取KV对的数量近似值 */
  long size();

  /** 移除缓存项：单个、批量或全部 */
  void invalidate(@CompatibleWith("K") Object key);
  void invalidateAll(Iterable<? extends Object> keys);
  void invalidateAll();

  /** 主动清理：包括移除过期的、弱引用或软引用的缓存想以释放内存资源 */
  void cleanUp();

  /** 获取统计数据快照 */
  CacheStats stats();

  /** 获取线程安全的缓存项视图Map */
  ConcurrentMap<K, V> asMap();

}
