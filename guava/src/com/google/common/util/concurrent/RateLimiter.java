package com.google.common.util.concurrent;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Internal.toNanosSaturated;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.SmoothRateLimiter.SmoothBursty;
import com.google.common.util.concurrent.SmoothRateLimiter.SmoothWarmingUp;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;

/**
 * 限流器基类
 *
 * @author Dimitris Andreou
 * @since 13.0
 */
@Beta
@J2ktIncompatible
@GwtIncompatible
@ElementTypesAreNonnullByDefault
public abstract class RateLimiter {

  /** 本地锁：保证串行 */
  @CheckForNull private volatile Object mutexDoNotUseDirectly;
  private Object mutex() {
    Object mutex = mutexDoNotUseDirectly;
    if (mutex == null) {
      synchronized (this) {
        mutex = mutexDoNotUseDirectly;
        if (mutex == null) {
          mutexDoNotUseDirectly = mutex = new Object();
        }
      }
    }
    return mutex;
  }

  /** 睡眠定时器：实现时间测量和等待功能，在许可证不可用时，可以计算出一个精确的等待时间 */
  private final SleepingStopwatch stopwatch;
  RateLimiter(SleepingStopwatch stopwatch) {
    this.stopwatch = checkNotNull(stopwatch);
  }
  abstract static class SleepingStopwatch {
    protected SleepingStopwatch() {}

    /** 时间测量：获取自创建以来的流逝时间，单位为微妙 */
    protected abstract long readMicros();

    /** 精确睡眠：在给定的微秒数内使当前线程进入休眠状态，在休眠期间会忽略中断请求完成整个睡眠周期 */
    protected abstract void sleepMicrosUninterruptibly(long micros);

    public static SleepingStopwatch createFromSystemTimer() {
      return new SleepingStopwatch() {
        final Stopwatch stopwatch = Stopwatch.createStarted();

        @Override
        protected long readMicros() {
          return stopwatch.elapsed(MICROSECONDS);
        }

        @Override
        protected void sleepMicrosUninterruptibly(long micros) {
          if (micros > 0) {
            Uninterruptibles.sleepUninterruptibly(micros, MICROSECONDS);
          }
        }
      };
    }
  }

  /** 创建限流器：默认是平滑限流器（突发模式），基于令牌桶算法，要求传入速率（即每秒发放的许可证/令牌数量） */
  public static RateLimiter create(double permitsPerSecond) {
    return create(permitsPerSecond, SleepingStopwatch.createFromSystemTimer());
  }
  @VisibleForTesting
  static RateLimiter create(double permitsPerSecond, SleepingStopwatch stopwatch) {
    RateLimiter rateLimiter = new SmoothBursty(stopwatch, 1.0);
    rateLimiter.setRate(permitsPerSecond);
    return rateLimiter;
  }

  /** 创建限流器：默认是平滑限流器（预热模式），基于令牌桶算法，要求传入速率（即每秒发放的许可证/令牌数量）、预热周期 */
  public static RateLimiter create(double permitsPerSecond, Duration warmupPeriod) {
    return create(permitsPerSecond, toNanosSaturated(warmupPeriod), TimeUnit.NANOSECONDS);
  }
  @SuppressWarnings("GoodTime")
  public static RateLimiter create(double permitsPerSecond, long warmupPeriod, TimeUnit unit) {
    checkArgument(warmupPeriod >= 0, "warmupPeriod must not be negative: %s", warmupPeriod);
    return create(
        permitsPerSecond, warmupPeriod, unit, 3.0, SleepingStopwatch.createFromSystemTimer());
  }
  @VisibleForTesting
  static RateLimiter create(
      double permitsPerSecond,
      long warmupPeriod,
      TimeUnit unit,
      double coldFactor,
      SleepingStopwatch stopwatch) {
    RateLimiter rateLimiter = new SmoothWarmingUp(stopwatch, warmupPeriod, unit, coldFactor);
    rateLimiter.setRate(permitsPerSecond);
    return rateLimiter;
  }

  /** 设置速率 */
  public final void setRate(double permitsPerSecond) {
    checkArgument(permitsPerSecond > 0.0 && !Double.isNaN(permitsPerSecond), "rate must be positive");
    synchronized (mutex()) {
      doSetRate(permitsPerSecond, stopwatch.readMicros());
    }
  }
  abstract void doSetRate(double permitsPerSecond, long nowMicros);

  /** 获取速率 */
  public final double getRate() {
    synchronized (mutex()) {
      return doGetRate();
    }
  }
  abstract double doGetRate();

  /** 获取令牌：阻塞当前线程，直到成功获取指定数量的令牌；若当前令牌数量不足，则当前线程会被挂起，直到有足够的令牌被创建 */
  @CanIgnoreReturnValue
  public double acquire() {
    return acquire(1);
  }
  @CanIgnoreReturnValue
  public double acquire(int permits) {
    // 先计算当前请求线程要等待的微秒数（惰性计算），然后精确睡眠等待，最后返回等待的秒数
    long microsToWait = reserve(permits);
    stopwatch.sleepMicrosUninterruptibly(microsToWait);
    return 1.0 * microsToWait / SECONDS.toMicros(1L);
  }

  /** 获取令牌：在超时时间内无法获取许可，则立即返回false，而不会继续阻塞 */
  public boolean tryAcquire(Duration timeout) {
    return tryAcquire(1, toNanosSaturated(timeout), TimeUnit.NANOSECONDS);
  }
  @SuppressWarnings("GoodTime")
  public boolean tryAcquire(long timeout, TimeUnit unit) {
    return tryAcquire(1, timeout, unit);
  }
  public boolean tryAcquire(int permits) {
    return tryAcquire(permits, 0, MICROSECONDS);
  }
  public boolean tryAcquire() {
    return tryAcquire(1, 0, MICROSECONDS);
  }
  public boolean tryAcquire(int permits, Duration timeout) {
    return tryAcquire(permits, toNanosSaturated(timeout), TimeUnit.NANOSECONDS);
  }
  @SuppressWarnings("GoodTime")
  public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
    long timeoutMicros = max(unit.toMicros(timeout), 0);
    checkPermits(permits);
    long microsToWait;
    synchronized (mutex()) {
      long nowMicros = stopwatch.readMicros();
      if (!canAcquire(nowMicros, timeoutMicros)) {
        return false;
      } else {
        microsToWait = reserveAndGetWaitLength(permits, nowMicros);
      }
    }
    stopwatch.sleepMicrosUninterruptibly(microsToWait);
    return true;
  }

  /** 基于要获取的令牌数量，计算当前请求线程要等待的微秒数 */
  final long reserve(int permits) {
    checkPermits(permits);
    synchronized (mutex()) {
      return reserveAndGetWaitLength(permits, stopwatch.readMicros());
    }
  }
  private static void checkPermits(int permits) {
    checkArgument(permits > 0, "Requested permits (%s) must be positive", permits);
  }

  /** 基于要获取的令牌数量、当前微秒数，计算当前线程要等待的微秒数 */
  final long reserveAndGetWaitLength(int permits, long nowMicros) {
    long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
    return max(momentAvailable - nowMicros, 0);
  }

  /** 核心算法实现：基于要获取的令牌数量、当前时间，计算可得到这些令牌的最早时间 */
  abstract long reserveEarliestAvailable(int permits, long nowMicros);

  /** 检查是否可获取到令牌 */
  private boolean canAcquire(long nowMicros, long timeoutMicros) {
    return queryEarliestAvailable(nowMicros) - timeoutMicros <= nowMicros;
  }

  /** 下一次获取令牌时预计等待的微秒数 */
  abstract long queryEarliestAvailable(long nowMicros);

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "RateLimiter[stableRate=%3.1fqps]", getRate());
  }

}
