package com.google.common.eventbus;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.j2objc.annotations.Weak;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import javax.annotation.CheckForNull;

/**
 * 事件订阅者
 *
 * @author Colin Decker
 */
@ElementTypesAreNonnullByDefault
class Subscriber {

  /** 归属的事件总线、归属的监听器对象、归属的订阅者方法、归属的事件执行器 */
  @Weak private EventBus bus;
  @VisibleForTesting final Object target;
  private final Method method;
  private final Executor executor;

  /** 构造方法 */
  static Subscriber create(EventBus bus, Object listener, Method method) {
    // 基于该方法是否为线程安全的，构造不同的订阅者
    return isDeclaredThreadSafe(method)
        ? new Subscriber(bus, listener, method)
        : new SynchronizedSubscriber(bus, listener, method);
  }
  /** 检查该方法是否声明了线程安全：若带有AllowConcurrentEvents注解，则为线程安全的*/
  private static boolean isDeclaredThreadSafe(Method method) {
    return method.getAnnotation(AllowConcurrentEvents.class) != null;
  }
  private Subscriber(EventBus bus, Object target, Method method) {
    this.bus = bus;
    this.target = checkNotNull(target);
    this.method = method;
    method.setAccessible(true);
    this.executor = bus.executor();
  }

  /** 分发事件：可采用合适的任务执行器（默认当前线程同步执行，可自定义线程池异步执行） */
  final void dispatchEvent(Object event) {
    executor.execute(
        () -> {
          try {
            // 调用事件订阅者方法消费执行事件
            invokeSubscriberMethod(event);
          } catch (InvocationTargetException e) {
            // 处理订阅者执行事件时抛出的异常
            bus.handleSubscriberException(e.getCause(), context(event));
          }
        });
  }

  /** 调用事件订阅者方法消费执行事件 */
  @VisibleForTesting
  void invokeSubscriberMethod(Object event) throws InvocationTargetException {
    try {
      // 调用事件订阅者方法消费执行事件
      method.invoke(target, checkNotNull(event));
    } catch (IllegalArgumentException e) {
      throw new Error("Method rejected target/argument: " + event, e);
    } catch (IllegalAccessException e) {
      throw new Error("Method became inaccessible: " + event, e);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      throw e;
    }
  }

  /** 获取订阅者消费执行事件抛出的异常上下文 */
  private SubscriberExceptionContext context(Object event) {
    return new SubscriberExceptionContext(bus, event, target, method);
  }

  @Override
  public final int hashCode() {
    return (31 + method.hashCode()) * 31 + System.identityHashCode(target);
  }

  @Override
  public final boolean equals(@CheckForNull Object obj) {
    if (obj instanceof Subscriber) {
      Subscriber that = (Subscriber) obj;
      return target == that.target && method.equals(that.method);
    }
    return false;
  }

  /** 同步事件订阅者：本地锁保证订阅者方法串行执行 */
  @VisibleForTesting
  static final class SynchronizedSubscriber extends Subscriber {

    private SynchronizedSubscriber(EventBus bus, Object target, Method method) {
      super(bus, target, method);
    }

    @Override
    void invokeSubscriberMethod(Object event) throws InvocationTargetException {
      // 本地锁保证订阅者方法串行执行
      synchronized (this) {
        super.invokeSubscriberMethod(event);
      }
    }
  }

}
