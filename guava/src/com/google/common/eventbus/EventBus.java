package com.google.common.eventbus;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.MoreExecutors;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 事件总线：负责分发事件给订阅者，并提供订阅者注册与卸载的能力
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@ElementTypesAreNonnullByDefault
public class EventBus {
  private static final Logger logger = Logger.getLogger(EventBus.class.getName());

  /** 唯一标识符、事件处理执行器（默认是当前线程同步执行、可自定义线程池）、事件订阅者执行异常处理器、订阅者注册中心、事件分发器 */
  private final String identifier;
  private final Executor executor;
  private final SubscriberExceptionHandler exceptionHandler;
  private final SubscriberRegistry subscribers = new SubscriberRegistry(this);
  private final Dispatcher dispatcher;
  public final String identifier() {
    return identifier;
  }
  final Executor executor() {
    return executor;
  }

  /** 构造方法 */
  public EventBus() {
    this("default");
  }
  public EventBus(String identifier) {
    // 默认使用DirectExecutor事件处理执行器（当前线程负责执行）、线程级队列事件分发器、事件订阅者执行异常处理器（仅仅记录日志）
    this(
        identifier,
        MoreExecutors.directExecutor(),
        Dispatcher.perThreadDispatchQueue(),
        LoggingHandler.INSTANCE);
  }
  public EventBus(SubscriberExceptionHandler exceptionHandler) {
    this(
        "default",
        MoreExecutors.directExecutor(),
        Dispatcher.perThreadDispatchQueue(),
        exceptionHandler);
  }
  EventBus(
      String identifier,
      Executor executor,
      Dispatcher dispatcher,
      SubscriberExceptionHandler exceptionHandler) {
    this.identifier = checkNotNull(identifier);
    this.executor = checkNotNull(executor);
    this.dispatcher = checkNotNull(dispatcher);
    this.exceptionHandler = checkNotNull(exceptionHandler);
  }

  /** 注册事件订阅者 */
  public void register(Object object) {
    subscribers.register(object);
  }

  /** 卸载事件订阅者 */
  public void unregister(Object object) {
    subscribers.unregister(object);
  }

  /** 发送事件 */
  public void post(Object event) {
    // 获取该事件的所有订阅者
    Iterator<Subscriber> eventSubscribers = subscribers.getSubscribers(event);
    if (eventSubscribers.hasNext()) {
      // 分发该事件给所有订阅者
      dispatcher.dispatch(event, eventSubscribers);
    } else if (!(event instanceof DeadEvent)) {
      // 发送死亡事件（指没有订阅者的事件）
      post(new DeadEvent(this, event));
    }
  }

  /** 处理订阅者执行事件时抛出的异常 */
  void handleSubscriberException(Throwable e, SubscriberExceptionContext context) {
    checkNotNull(e);
    checkNotNull(context);
    try {
      exceptionHandler.handleException(e, context);
    } catch (Throwable e2) {
      logger.log(
              Level.SEVERE,
              String.format(Locale.ROOT, "Exception %s thrown while handling exception: %s", e2, e),
              e2);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(identifier).toString();
  }

  /** 事件订阅者执行异常处理器：仅仅记录日志 */
  static final class LoggingHandler implements SubscriberExceptionHandler {
    static final LoggingHandler INSTANCE = new LoggingHandler();

    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
      Logger logger = logger(context);
      if (logger.isLoggable(Level.SEVERE)) {
        logger.log(Level.SEVERE, message(context), exception);
      }
    }

    private static Logger logger(SubscriberExceptionContext context) {
      return Logger.getLogger(EventBus.class.getName() + "." + context.getEventBus().identifier());
    }

    private static String message(SubscriberExceptionContext context) {
      Method method = context.getSubscriberMethod();
      return "Exception thrown by subscriber method "
          + method.getName()
          + '('
          + method.getParameterTypes()[0].getName()
          + ')'
          + " on subscriber "
          + context.getSubscriber()
          + " when dispatching event: "
          + context.getEvent();
    }
  }

}
