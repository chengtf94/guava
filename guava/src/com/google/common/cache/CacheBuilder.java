package com.google.common.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.base.Equivalence;
import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;
import com.google.common.cache.AbstractCache.SimpleStatsCounter;
import com.google.common.cache.AbstractCache.StatsCounter;
import com.google.common.cache.LocalCache.Strength;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.j2objc.annotations.J2ObjCIncompatible;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

/**
 * KV缓存容器建造器：基于建造者模式
 *
 * @author Charles Fry
 * @author Kevin Bourrillion
 * @since 10.0
 */
@GwtCompatible(emulated = true)
@ElementTypesAreNonnullByDefault
public final class CacheBuilder<K, V> {
  private static final class LoggerHolder {
    static final Logger logger = Logger.getLogger(CacheBuilder.class.getName());
  }

  /** 构建KV缓存容器（自动加载模式）：支持自动加载 */
  public <K1 extends K, V1 extends V> LoadingCache<K1, V1> build(
          CacheLoader<? super K1, V1> loader) {
    checkWeightWithWeigher();
    return new LocalCache.LocalLoadingCache<>(this, loader);
  }

  /** 构建KV缓存容器（手动模式）：不会自动加载，要在调用时手动关联CacheLoader实现加载 */
  public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
    checkWeightWithWeigher();
    checkNonLoadingCache();
    return new LocalCache.LocalManualCache<>(this);
  }

  /** 默认初始容量、默认并发级别（segments分段数量）、默认过期时间、默认刷新时间 */
  private static final int DEFAULT_INITIAL_CAPACITY = 16;
  private static final int DEFAULT_CONCURRENCY_LEVEL = 4;
  @SuppressWarnings("GoodTime")
  private static final int DEFAULT_EXPIRATION_NANOS = 0;
  @SuppressWarnings("GoodTime")
  private static final int DEFAULT_REFRESH_NANOS = 0;
  static final int UNSET_INT = -1;

  /** 初始容量、并发级别（segments分段数量）、最大容量、最大权重、缓存项权重计算器 */
  int initialCapacity = UNSET_INT;
  int concurrencyLevel = UNSET_INT;
  long maximumSize = UNSET_INT;
  long maximumWeight = UNSET_INT;
  @CheckForNull Weigher<? super K, ? super V> weigher;

  /** 过期策略：写后过期时间间隔、访问后后过期时间间隔 */
  @SuppressWarnings("GoodTime")
  long expireAfterWriteNanos = UNSET_INT;
  @SuppressWarnings("GoodTime")
  long expireAfterAccessNanos = UNSET_INT;

  /** 刷新策略：刷新时间间隔 */
  @SuppressWarnings("GoodTime")
  long refreshNanos = UNSET_INT;

  /** 时间源 */
  @CheckForNull Ticker ticker;

  /** 缓存项移除监听器 */
  @CheckForNull RemovalListener<? super K, ? super V> removalListener;

  /** 统计计数器 */
  Supplier<? extends StatsCounter> statsCounterSupplier = NULL_STATS_COUNTER;
  static final CacheStats EMPTY_STATS = new CacheStats(0, 0, 0, 0, 0, 0);
  static final Supplier<? extends StatsCounter> NULL_STATS_COUNTER =
          Suppliers.ofInstance(
                  new StatsCounter() {
                    @Override
                    public void recordHits(int count) {}

                    @Override
                    public void recordMisses(int count) {}

                    @SuppressWarnings("GoodTime")
                    @Override
                    public void recordLoadSuccess(long loadTime) {}

                    @SuppressWarnings("GoodTime")
                    @Override
                    public void recordLoadException(long loadTime) {}

                    @Override
                    public void recordEviction() {}

                    @Override
                    public CacheStats snapshot() {
                      return EMPTY_STATS;
                    }
                  });

  /** 构造方法 */
  private CacheBuilder() {}
  public static CacheBuilder<Object, Object> newBuilder() {
    return new CacheBuilder<>();
  }
  @GwtIncompatible // To be supported
  public static CacheBuilder<Object, Object> from(CacheBuilderSpec spec) {
    return spec.toCacheBuilder().lenientParsing();
  }
  @GwtIncompatible // To be supported
  public static CacheBuilder<Object, Object> from(String spec) {
    return from(CacheBuilderSpec.parse(spec));
  }

  /** 设置、获取初始容量 */
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> initialCapacity(int initialCapacity) {
    checkState(
        this.initialCapacity == UNSET_INT,
        "initial capacity was already set to %s",
        this.initialCapacity);
    checkArgument(initialCapacity >= 0);
    this.initialCapacity = initialCapacity;
    return this;
  }
  int getInitialCapacity() {
    return (initialCapacity == UNSET_INT) ? DEFAULT_INITIAL_CAPACITY : initialCapacity;
  }

  /** 设置、获取并发级别（segments分段数量） */
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> concurrencyLevel(int concurrencyLevel) {
    checkState(
        this.concurrencyLevel == UNSET_INT,
        "concurrency level was already set to %s",
        this.concurrencyLevel);
    checkArgument(concurrencyLevel > 0);
    this.concurrencyLevel = concurrencyLevel;
    return this;
  }
  int getConcurrencyLevel() {
    return (concurrencyLevel == UNSET_INT) ? DEFAULT_CONCURRENCY_LEVEL : concurrencyLevel;
  }

  /** 设置最大容量：与最大权重互斥 */
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> maximumSize(long maximumSize) {
    checkState(
        this.maximumSize == UNSET_INT, "maximum size was already set to %s", this.maximumSize);
    checkState(
        this.maximumWeight == UNSET_INT,
        "maximum weight was already set to %s",
        this.maximumWeight);
    checkState(this.weigher == null, "maximum size can not be combined with weigher");
    checkArgument(maximumSize >= 0, "maximum size must not be negative");
    this.maximumSize = maximumSize;
    return this;
  }

  /** 设置最大权重：与最大容量互斥 */
  @GwtIncompatible // To be supported
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> maximumWeight(long maximumWeight) {
    checkState(
        this.maximumWeight == UNSET_INT,
        "maximum weight was already set to %s",
        this.maximumWeight);
    checkState(
        this.maximumSize == UNSET_INT, "maximum size was already set to %s", this.maximumSize);
    checkArgument(maximumWeight >= 0, "maximum weight must not be negative");
    this.maximumWeight = maximumWeight;
    return this;
  }

  /** 获取最大权重：maximumSize or maximumWeight */
  long getMaximumWeight() {
    if (expireAfterWriteNanos == 0 || expireAfterAccessNanos == 0) {
      return 0;
    }
    return (weigher == null) ? maximumSize : maximumWeight;
  }

  /** 设置、获取缓存项权重计算器 */
  @GwtIncompatible
  @CanIgnoreReturnValue
  public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> weigher(
          Weigher<? super K1, ? super V1> weigher) {
    checkState(this.weigher == null);
    if (strictParsing) {
      checkState(
              this.maximumSize == UNSET_INT,
              "weigher can not be combined with maximum size (%s provided)",
              this.maximumSize);
    }
    @SuppressWarnings("unchecked")
    CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
    me.weigher = checkNotNull(weigher);
    return me;
  }
  @SuppressWarnings("unchecked")
  <K1 extends K, V1 extends V> Weigher<K1, V1> getWeigher() {
    return (Weigher<K1, V1>) MoreObjects.firstNonNull(weigher, OneWeigher.INSTANCE);
  }
  enum OneWeigher implements Weigher<Object, Object> {
    INSTANCE;
    @Override
    public int weigh(Object key, Object value) {
      return 1;
    }
  }
  private void checkWeightWithWeigher() {
    if (weigher == null) {
      checkState(maximumWeight == UNSET_INT, "maximumWeight requires weigher");
    } else {
      if (strictParsing) {
        checkState(maximumWeight != UNSET_INT, "weigher requires maximumWeight");
      } else {
        if (maximumWeight == UNSET_INT) {
          LoggerHolder.logger.log(
              Level.WARNING, "ignoring weigher specified without maximumWeight");
        }
      }
    }
  }

  /** 设置、获取过期策略：写后过期时间间隔 */
  @J2ObjCIncompatible
  @GwtIncompatible
  @SuppressWarnings("GoodTime")
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> expireAfterWrite(java.time.Duration duration) {
    return expireAfterWrite(toNanosSaturated(duration), TimeUnit.NANOSECONDS);
  }
  @SuppressWarnings("GoodTime")
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> expireAfterWrite(long duration, TimeUnit unit) {
    checkState(
            expireAfterWriteNanos == UNSET_INT,
            "expireAfterWrite was already set to %s ns",
            expireAfterWriteNanos);
    checkArgument(duration >= 0, "duration cannot be negative: %s %s", duration, unit);
    this.expireAfterWriteNanos = unit.toNanos(duration);
    return this;
  }
  @SuppressWarnings("GoodTime")
  long getExpireAfterWriteNanos() {
    return (expireAfterWriteNanos == UNSET_INT) ? DEFAULT_EXPIRATION_NANOS : expireAfterWriteNanos;
  }

  /** 设置、获取过期策略：访问后过期时间间隔 */
  @J2ObjCIncompatible
  @GwtIncompatible
  @SuppressWarnings("GoodTime")
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> expireAfterAccess(java.time.Duration duration) {
    return expireAfterAccess(toNanosSaturated(duration), TimeUnit.NANOSECONDS);
  }
  @SuppressWarnings("GoodTime")
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> expireAfterAccess(long duration, TimeUnit unit) {
    checkState(
            expireAfterAccessNanos == UNSET_INT,
            "expireAfterAccess was already set to %s ns",
            expireAfterAccessNanos);
    checkArgument(duration >= 0, "duration cannot be negative: %s %s", duration, unit);
    this.expireAfterAccessNanos = unit.toNanos(duration);
    return this;
  }
  @SuppressWarnings("GoodTime")
  long getExpireAfterAccessNanos() {
    return (expireAfterAccessNanos == UNSET_INT)
            ? DEFAULT_EXPIRATION_NANOS
            : expireAfterAccessNanos;
  }

  /** 设置、获取刷新策略：刷新时间间隔 */
  @J2ObjCIncompatible
  @GwtIncompatible
  @SuppressWarnings("GoodTime")
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> refreshAfterWrite(java.time.Duration duration) {
    return refreshAfterWrite(toNanosSaturated(duration), TimeUnit.NANOSECONDS);
  }
  @GwtIncompatible
  @SuppressWarnings("GoodTime")
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> refreshAfterWrite(long duration, TimeUnit unit) {
    checkNotNull(unit);
    checkState(refreshNanos == UNSET_INT, "refresh was already set to %s ns", refreshNanos);
    checkArgument(duration > 0, "duration must be positive: %s %s", duration, unit);
    this.refreshNanos = unit.toNanos(duration);
    return this;
  }
  @SuppressWarnings("GoodTime")
  long getRefreshNanos() {
    return (refreshNanos == UNSET_INT) ? DEFAULT_REFRESH_NANOS : refreshNanos;
  }
  private void checkNonLoadingCache() {
    checkState(refreshNanos == UNSET_INT, "refreshAfterWrite requires a LoadingCache");
  }

  /** 设置、获取实践源 */
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> ticker(Ticker ticker) {
    checkState(this.ticker == null);
    this.ticker = checkNotNull(ticker);
    return this;
  }
  Ticker getTicker(boolean recordsTime) {
    if (ticker != null) {
      return ticker;
    }
    return recordsTime ? Ticker.systemTicker() : NULL_TICKER;
  }
  static final Ticker NULL_TICKER =
          new Ticker() {
            @Override
            public long read() {
              return 0;
            }
          };

  /** 设置、获取缓存项监听器 */
  public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> removalListener(
          RemovalListener<? super K1, ? super V1> listener) {
    checkState(this.removalListener == null);
    @SuppressWarnings("unchecked")
    CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
    me.removalListener = checkNotNull(listener);
    return me;
  }
  @SuppressWarnings("unchecked")
  <K1 extends K, V1 extends V> RemovalListener<K1, V1> getRemovalListener() {
    return (RemovalListener<K1, V1>)
            MoreObjects.firstNonNull(removalListener, NullListener.INSTANCE);
  }
  enum NullListener implements RemovalListener<Object, Object> {
    INSTANCE;
    @Override
    public void onRemoval(RemovalNotification<Object, Object> notification) {}
  }

  /** 设置、获取统计计数器 */
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> recordStats() {
    statsCounterSupplier = CACHE_STATS_COUNTER;
    return this;
  }
  boolean isRecordingStats() {
    return statsCounterSupplier == CACHE_STATS_COUNTER;
  }
  Supplier<? extends StatsCounter> getStatsCounterSupplier() {
    return statsCounterSupplier;
  }
  @SuppressWarnings("AnonymousToLambda")
  static final Supplier<StatsCounter> CACHE_STATS_COUNTER =
          new Supplier<StatsCounter>() {
            @Override
            public StatsCounter get() {
              return new SimpleStatsCounter();
            }
          };

  /** 是否严格解析 */
  boolean strictParsing = true;
  @GwtIncompatible // To be supported
  @CanIgnoreReturnValue
  CacheBuilder<K, V> lenientParsing() {
    strictParsing = false;
    return this;
  }

  /** K-V引用策略 */
  @CheckForNull Strength keyStrength;
  @CheckForNull Strength valueStrength;

  @CanIgnoreReturnValue
  CacheBuilder<K, V> setKeyStrength(Strength strength) {
    checkState(keyStrength == null, "Key strength was already set to %s", keyStrength);
    keyStrength = checkNotNull(strength);
    return this;
  }
  Strength getKeyStrength() {
    return MoreObjects.firstNonNull(keyStrength, Strength.STRONG);
  }
  @GwtIncompatible
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> weakKeys() {
    return setKeyStrength(Strength.WEAK);
  }
  @GwtIncompatible
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> weakValues() {
    return setValueStrength(Strength.WEAK);
  }
  @GwtIncompatible // java.lang.ref.SoftReference
  @CanIgnoreReturnValue
  public CacheBuilder<K, V> softValues() {
    return setValueStrength(Strength.SOFT);
  }
  @CanIgnoreReturnValue
  CacheBuilder<K, V> setValueStrength(Strength strength) {
    checkState(valueStrength == null, "Value strength was already set to %s", valueStrength);
    valueStrength = checkNotNull(strength);
    return this;
  }
  Strength getValueStrength() {
    return MoreObjects.firstNonNull(valueStrength, Strength.STRONG);
  }


  /** K-V等价器：用于比较K或V是否相等 */
  @CheckForNull Equivalence<Object> keyEquivalence;
  @CheckForNull Equivalence<Object> valueEquivalence;

  @GwtIncompatible
  @CanIgnoreReturnValue
  CacheBuilder<K, V> keyEquivalence(Equivalence<Object> equivalence) {
    checkState(keyEquivalence == null, "key equivalence was already set to %s", keyEquivalence);
    keyEquivalence = checkNotNull(equivalence);
    return this;
  }
  Equivalence<Object> getKeyEquivalence() {
    return MoreObjects.firstNonNull(keyEquivalence, getKeyStrength().defaultEquivalence());
  }
  @GwtIncompatible
  @CanIgnoreReturnValue
  CacheBuilder<K, V> valueEquivalence(Equivalence<Object> equivalence) {
    checkState(
            valueEquivalence == null, "value equivalence was already set to %s", valueEquivalence);
    this.valueEquivalence = checkNotNull(equivalence);
    return this;
  }
  Equivalence<Object> getValueEquivalence() {
    return MoreObjects.firstNonNull(valueEquivalence, getValueStrength().defaultEquivalence());
  }

  @GwtIncompatible
  @SuppressWarnings("GoodTime")
  private static long toNanosSaturated(java.time.Duration duration) {
    // Using a try/catch seems lazy, but the catch block will rarely get invoked (except for
    // durations longer than approximately +/- 292 years).
    try {
      return duration.toNanos();
    } catch (ArithmeticException tooBig) {
      return duration.isNegative() ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper s = MoreObjects.toStringHelper(this);
    if (initialCapacity != UNSET_INT) {
      s.add("initialCapacity", initialCapacity);
    }
    if (concurrencyLevel != UNSET_INT) {
      s.add("concurrencyLevel", concurrencyLevel);
    }
    if (maximumSize != UNSET_INT) {
      s.add("maximumSize", maximumSize);
    }
    if (maximumWeight != UNSET_INT) {
      s.add("maximumWeight", maximumWeight);
    }
    if (expireAfterWriteNanos != UNSET_INT) {
      s.add("expireAfterWrite", expireAfterWriteNanos + "ns");
    }
    if (expireAfterAccessNanos != UNSET_INT) {
      s.add("expireAfterAccess", expireAfterAccessNanos + "ns");
    }
    if (keyStrength != null) {
      s.add("keyStrength", Ascii.toLowerCase(keyStrength.toString()));
    }
    if (valueStrength != null) {
      s.add("valueStrength", Ascii.toLowerCase(valueStrength.toString()));
    }
    if (keyEquivalence != null) {
      s.addValue("keyEquivalence");
    }
    if (valueEquivalence != null) {
      s.addValue("valueEquivalence");
    }
    if (removalListener != null) {
      s.addValue("removalListener");
    }
    return s.toString();
  }

}
