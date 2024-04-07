package com.google.common.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.math.LongMath.saturatedAdd;
import static com.google.common.math.LongMath.saturatedSubtract;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.concurrent.Callable;
import javax.annotation.CheckForNull;

/** 缓存性能统计数据：immutable不可变的
 *
 * @author Charles Fry
 * @since 10.0
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
public final class CacheStats {

  /** 命中次数、未命中次数、加载成功次数、加载失败次数、总加载时间、淘汰次数 */
  private final long hitCount;
  private final long missCount;
  private final long loadSuccessCount;
  private final long loadExceptionCount;
  @SuppressWarnings("GoodTime")
  private final long totalLoadTime;
  private final long evictionCount;
  public long hitCount() {
    return hitCount;
  }
  public long missCount() {
    return missCount;
  }
  public long loadSuccessCount() {
    return loadSuccessCount;
  }
  public long loadExceptionCount() {
    return loadExceptionCount;
  }
  @SuppressWarnings("GoodTime")
  public long totalLoadTime() {
    return totalLoadTime;
  }
  public long evictionCount() {
    return evictionCount;
  }

  /** 构造方法 */
  @SuppressWarnings("GoodTime")
  public CacheStats(
      long hitCount,
      long missCount,
      long loadSuccessCount,
      long loadExceptionCount,
      long totalLoadTime,
      long evictionCount) {
    checkArgument(hitCount >= 0);
    checkArgument(missCount >= 0);
    checkArgument(loadSuccessCount >= 0);
    checkArgument(loadExceptionCount >= 0);
    checkArgument(totalLoadTime >= 0);
    checkArgument(evictionCount >= 0);
    this.hitCount = hitCount;
    this.missCount = missCount;
    this.loadSuccessCount = loadSuccessCount;
    this.loadExceptionCount = loadExceptionCount;
    this.totalLoadTime = totalLoadTime;
    this.evictionCount = evictionCount;
  }

  /** 计算总请求次数 */
  public long requestCount() {
    return saturatedAdd(hitCount, missCount);
  }

  /** 计算命中率 */
  public double hitRate() {
    long requestCount = requestCount();
    return (requestCount == 0) ? 1.0 : (double) hitCount / requestCount;
  }

  /** 计算未命中率 */
  public double missRate() {
    long requestCount = requestCount();
    return (requestCount == 0) ? 0.0 : (double) missCount / requestCount;
  }

  /** 计算总加载次数 */
  public long loadCount() {
    return saturatedAdd(loadSuccessCount, loadExceptionCount);
  }

  /** 计算加载失败率 */
  public double loadExceptionRate() {
    long totalLoadCount = saturatedAdd(loadSuccessCount, loadExceptionCount);
    return (totalLoadCount == 0) ? 0.0 : (double) loadExceptionCount / totalLoadCount;
  }
  /** 计算平均加载时间 */
  public double averageLoadPenalty() {
    long totalLoadCount = saturatedAdd(loadSuccessCount, loadExceptionCount);
    return (totalLoadCount == 0) ? 0.0 : (double) totalLoadTime / totalLoadCount;
  }

  public CacheStats minus(CacheStats other) {
    return new CacheStats(
        Math.max(0, saturatedSubtract(hitCount, other.hitCount)),
        Math.max(0, saturatedSubtract(missCount, other.missCount)),
        Math.max(0, saturatedSubtract(loadSuccessCount, other.loadSuccessCount)),
        Math.max(0, saturatedSubtract(loadExceptionCount, other.loadExceptionCount)),
        Math.max(0, saturatedSubtract(totalLoadTime, other.totalLoadTime)),
        Math.max(0, saturatedSubtract(evictionCount, other.evictionCount)));
  }

  public CacheStats plus(CacheStats other) {
    return new CacheStats(
        saturatedAdd(hitCount, other.hitCount),
        saturatedAdd(missCount, other.missCount),
        saturatedAdd(loadSuccessCount, other.loadSuccessCount),
        saturatedAdd(loadExceptionCount, other.loadExceptionCount),
        saturatedAdd(totalLoadTime, other.totalLoadTime),
        saturatedAdd(evictionCount, other.evictionCount));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        hitCount, missCount, loadSuccessCount, loadExceptionCount, totalLoadTime, evictionCount);
  }

  @Override
  public boolean equals(@CheckForNull Object object) {
    if (object instanceof CacheStats) {
      CacheStats other = (CacheStats) object;
      return hitCount == other.hitCount
          && missCount == other.missCount
          && loadSuccessCount == other.loadSuccessCount
          && loadExceptionCount == other.loadExceptionCount
          && totalLoadTime == other.totalLoadTime
          && evictionCount == other.evictionCount;
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("hitCount", hitCount)
        .add("missCount", missCount)
        .add("loadSuccessCount", loadSuccessCount)
        .add("loadExceptionCount", loadExceptionCount)
        .add("totalLoadTime", totalLoadTime)
        .add("evictionCount", evictionCount)
        .toString();
  }

}
