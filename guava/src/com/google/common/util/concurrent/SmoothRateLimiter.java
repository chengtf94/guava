package com.google.common.util.concurrent;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.math.LongMath;
import java.util.concurrent.TimeUnit;


/**
 * 平滑限流器
 *
 * @author Dimitris Andreou
 * @since 13.0
 */
@J2ktIncompatible
@GwtIncompatible
@ElementTypesAreNonnullByDefault
abstract class SmoothRateLimiter extends RateLimiter {

  /** 最大令牌数、当前令牌数、生成一个令牌所需的时间（1/QPS）、下一次请求可获取到令牌的时间（可服务的时间，在在此之前则睡眠到该时间点才能获得令牌） */
  double maxPermits;
  double storedPermits;
  double stableIntervalMicros;
  private long nextFreeTicketMicros = 0L;

  /** 构造方法 */
  private SmoothRateLimiter(SleepingStopwatch stopwatch) {
    super(stopwatch);
  }

  /** 设置速率 */
  @Override
  final void doSetRate(double permitsPerSecond, long nowMicros) {
    resync(nowMicros);
    double stableIntervalMicros = SECONDS.toMicros(1L) / permitsPerSecond;
    this.stableIntervalMicros = stableIntervalMicros;
    doSetRate(permitsPerSecond, stableIntervalMicros);
  }
  abstract void doSetRate(double permitsPerSecond, double stableIntervalMicros);

  /**  核心算法实现：基于要获取的令牌数量、当前时间，计算可得到这些令牌的最早时间 */
  @Override
  final long reserveEarliestAvailable(int requiredPermits, long nowMicros) {
    // #1 根据当前时间和下一次请求可获取到令牌的时间判断有无新令牌产⽣，有则更新当前令牌数、下一次请求可获取到令牌的时间
    resync(nowMicros);
    // #2 计算持可⽤的令牌数量、需要预⽀的令牌数量
    long returnValue = nextFreeTicketMicros;
    double storedPermitsToSpend = min(requiredPermits, this.storedPermits);
    double freshPermits = requiredPermits - storedPermitsToSpend;
    // #3 计算下一次请求需要等待的时间：等待时间 = 桶内令牌发放时间（突发模式下为0） + 生成预支令牌所需时间
    long waitMicros = storedPermitsToWaitTime(this.storedPermits, storedPermitsToSpend)
            + (long) (freshPermits * stableIntervalMicros);
    // #4 更新下一次请求可获取到令牌的时间、当前令牌数
    this.nextFreeTicketMicros = LongMath.saturatedAdd(nextFreeTicketMicros, waitMicros);
    this.storedPermits -= storedPermitsToSpend;
    return returnValue;
  }

  /** 重新同步：只有当nowMicros > nextFreeTicketMicros时才更新当前令牌数、下一次请求可获取到令牌的时间  */
  void resync(long nowMicros) {
    if (nowMicros > nextFreeTicketMicros) {
      // 当前令牌数 = min(最大令牌数， 剩余令牌数 + 空闲时间内生成的令牌数)
      // nextFreeTicketMicros = now
      double newPermits = (nowMicros - nextFreeTicketMicros) / coolDownIntervalMicros();
      storedPermits = min(maxPermits, storedPermits + newPermits);
      nextFreeTicketMicros = nowMicros;
    }
  }
  /** 获取冷却期等待新令牌的微秒数 */
  abstract double coolDownIntervalMicros();

  /** 获取速率 */
  @Override
  final double doGetRate() {
    return SECONDS.toMicros(1L) / stableIntervalMicros;
  }

  /** 获取生成下一个可用令牌预计等待的微秒数 */
  @Override
  final long queryEarliestAvailable(long nowMicros) {
    return nextFreeTicketMicros;
  }

  abstract long storedPermitsToWaitTime(double storedPermits, double permitsToTake);


  /****************************** 平滑限流器（突增模式）：突增是指允许短暂的速率峰值 ****************************/
  static final class SmoothBursty extends SmoothRateLimiter {

    /** 最大突发持续时间：默认为1秒，用于设置允许的最大突发事件窗口 */
    final double maxBurstSeconds;

    /** 构造方法 */
    SmoothBursty(SleepingStopwatch stopwatch, double maxBurstSeconds) {
      super(stopwatch);
      this.maxBurstSeconds = maxBurstSeconds;
    }

    @Override
    void doSetRate(double permitsPerSecond, double stableIntervalMicros) {
      // 设置令牌桶最大容量 = 最大突发持续时间 * 速率
      // 设置令牌桶当前容量 = 0
      double oldMaxPermits = this.maxPermits;
      maxPermits = maxBurstSeconds * permitsPerSecond;
      if (oldMaxPermits == Double.POSITIVE_INFINITY) {
        // if we don't special-case this, we would get storedPermits == NaN, below
        storedPermits = maxPermits;
      } else {
        storedPermits =
                (oldMaxPermits == 0.0)
                        ? 0.0 // initial state
                        : storedPermits * maxPermits / oldMaxPermits;
      }
    }

    @Override
    long storedPermitsToWaitTime(double storedPermits, double permitsToTake) {
      return 0L;
    }

    @Override
    double coolDownIntervalMicros() {
      return stableIntervalMicros;
    }
  }


  /****************************** 平滑限流器（预热模式）：预热是指逐步增加速率的过程 ****************************/
  /**
   *          ^ throttling
   *          |
   *    cold  +                  /
   * interval |                 /.
   *          |                / .
   *          |               /  .   ← "warmup period" is the area of the trapezoid between
   *          |              /   .     thresholdPermits and maxPermits
   *          |             /    .
   *          |            /     .
   *          |           /      .
   *   stable +----------/  WARM .
   * interval |          .   UP  .
   *          |          . PERIOD.
   *          |          .       .
   *        0 +----------+-------+--------------→ storedPermits
   *          0 thresholdPermits maxPermits
   */
  static final class SmoothWarmingUp extends SmoothRateLimiter {

    /** 预热周期微秒数、梯形斜边的斜率、门槛令牌数量（门槛是指？）、冷却因子 */
    private final long warmupPeriodMicros;
    private double slope;
    private double thresholdPermits;
    private double coldFactor;

    /** 构造方法 */
    SmoothWarmingUp(SleepingStopwatch stopwatch, long warmupPeriod, TimeUnit timeUnit, double coldFactor) {
      super(stopwatch);
      this.warmupPeriodMicros = timeUnit.toMicros(warmupPeriod);
      this.coldFactor = coldFactor;
    }

    @Override
    void doSetRate(double permitsPerSecond, double stableIntervalMicros) {
      double oldMaxPermits = maxPermits;
      double coldIntervalMicros = stableIntervalMicros * coldFactor;
      thresholdPermits = 0.5 * warmupPeriodMicros / stableIntervalMicros;
      maxPermits =
          thresholdPermits + 2.0 * warmupPeriodMicros / (stableIntervalMicros + coldIntervalMicros);
      slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits - thresholdPermits);
      if (oldMaxPermits == Double.POSITIVE_INFINITY) {
        // if we don't special-case this, we would get storedPermits == NaN, below
        storedPermits = 0.0;
      } else {
        storedPermits =
            (oldMaxPermits == 0.0)
                ? maxPermits // initial state is cold
                : storedPermits * maxPermits / oldMaxPermits;
      }
    }

    @Override
    long storedPermitsToWaitTime(double storedPermits, double permitsToTake) {
      double availablePermitsAboveThreshold = storedPermits - thresholdPermits;
      long micros = 0;
      // measuring the integral on the right part of the function (the climbing line)
      // 预热期发放令牌的时间，即梯形的面积
      if (availablePermitsAboveThreshold > 0.0) {
        double permitsAboveThresholdToTake = min(availablePermitsAboveThreshold, permitsToTake);
        // TODO(cpovirk): Figure out a good name for this variable.
        double length =
            permitsToTime(availablePermitsAboveThreshold)
                + permitsToTime(availablePermitsAboveThreshold - permitsAboveThresholdToTake);
        micros = (long) (permitsAboveThresholdToTake * length / 2.0);
        permitsToTake -= permitsAboveThresholdToTake;
      }
      // measuring the integral on the left part of the function (the horizontal line)
      // 预热期发放令牌的时间，即矩形的面积
      micros += (long) (stableIntervalMicros * permitsToTake);
      return micros;
    }

    private double permitsToTime(double permits) {
      return stableIntervalMicros + permits * slope;
    }

    @Override
    double coolDownIntervalMicros() {
      return warmupPeriodMicros / maxPermits;
    }
  }

}
