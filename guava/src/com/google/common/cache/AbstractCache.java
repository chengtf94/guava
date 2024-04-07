package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * KV缓存容器基类
 *
 * @author Charles Fry
 * @since 10.0
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
public abstract class AbstractCache<K, V> implements Cache<K, V> {
  protected AbstractCache() {}

  /** 统计计数器接口 */
  public interface StatsCounter {

    /**记录命中次数、未命中次数、加载成功次数、加载失败次数、淘汰次数 */
    void recordHits(int count);
    void recordMisses(int count);
    @SuppressWarnings("GoodTime")
    void recordLoadSuccess(long loadTime);
    @SuppressWarnings("GoodTime")
    void recordLoadException(long loadTime);
    void recordEviction();

    /** 获取统计数据快照 */
    CacheStats snapshot();
  }

  /** 简单统计计数器：线程安全 */
  public static final class SimpleStatsCounter implements StatsCounter {
    public SimpleStatsCounter() {}

    /** 命中次数、未命中次数、加载成功次数、加载失败次数、总加载时间、淘汰次数 */
    private final LongAddable hitCount = LongAddables.create();
    private final LongAddable missCount = LongAddables.create();
    private final LongAddable loadSuccessCount = LongAddables.create();
    private final LongAddable loadExceptionCount = LongAddables.create();
    private final LongAddable totalLoadTime = LongAddables.create();
    private final LongAddable evictionCount = LongAddables.create();

    @Override
    public void recordHits(int count) {
      hitCount.add(count);
    }

    @Override
    public void recordMisses(int count) {
      missCount.add(count);
    }

    @SuppressWarnings("GoodTime")
    @Override
    public void recordLoadSuccess(long loadTime) {
      loadSuccessCount.increment();
      totalLoadTime.add(loadTime);
    }

    @SuppressWarnings("GoodTime")
    @Override
    public void recordLoadException(long loadTime) {
      loadExceptionCount.increment();
      totalLoadTime.add(loadTime);
    }

    @Override
    public void recordEviction() {
      evictionCount.increment();
    }

    @Override
    public CacheStats snapshot() {
      return new CacheStats(
              negativeToMaxValue(hitCount.sum()),
              negativeToMaxValue(missCount.sum()),
              negativeToMaxValue(loadSuccessCount.sum()),
              negativeToMaxValue(loadExceptionCount.sum()),
              negativeToMaxValue(totalLoadTime.sum()),
              negativeToMaxValue(evictionCount.sum()));
    }

    private static long negativeToMaxValue(long value) {
      return (value >= 0) ? value : Long.MAX_VALUE;
    }

    public void incrementBy(StatsCounter other) {
      CacheStats otherStats = other.snapshot();
      hitCount.add(otherStats.hitCount());
      missCount.add(otherStats.missCount());
      loadSuccessCount.add(otherStats.loadSuccessCount());
      loadExceptionCount.add(otherStats.loadExceptionCount());
      totalLoadTime.add(otherStats.totalLoadTime());
      evictionCount.add(otherStats.evictionCount());
    }
  }

  @Override
  public void put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public V get(K key, Callable<? extends V> valueLoader) throws ExecutionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableMap<K, V> getAllPresent(Iterable<? extends Object> keys) {
    Map<K, V> result = Maps.newLinkedHashMap();
    for (Object key : keys) {
      if (!result.containsKey(key)) {
        @SuppressWarnings("unchecked")
        K castKey = (K) key;
        V value = getIfPresent(key);
        if (value != null) {
          result.put(castKey, value);
        }
      }
    }
    return ImmutableMap.copyOf(result);
  }

  @Override
  public long size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invalidate(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  // For discussion of <? extends Object>, see getAllPresent.
  public void invalidateAll(Iterable<? extends Object> keys) {
    for (Object key : keys) {
      invalidate(key);
    }
  }

  @Override
  public void invalidateAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanUp() {}

  @Override
  public CacheStats stats() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    throw new UnsupportedOperationException();
  }

}
