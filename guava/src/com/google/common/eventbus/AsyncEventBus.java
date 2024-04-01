package com.google.common.eventbus;

import java.util.concurrent.Executor;

/**
 * 异步事件总线：与同步事件总线的区别是要求必须传入自定义的执行器，一般是自定义业务线程池
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@ElementTypesAreNonnullByDefault
public class AsyncEventBus extends EventBus {

  /** 构造方法 */
  public AsyncEventBus(String identifier, Executor executor) {
    // 要求必须传入自定义的执行器，默认使用全局队列事件分发器
    super(identifier, executor, Dispatcher.legacyAsync(), LoggingHandler.INSTANCE);
  }
  public AsyncEventBus(Executor executor, SubscriberExceptionHandler subscriberExceptionHandler) {
    super("default", executor, Dispatcher.legacyAsync(), subscriberExceptionHandler);
  }
  public AsyncEventBus(Executor executor) {
    super("default", executor, Dispatcher.legacyAsync(), LoggingHandler.INSTANCE);
  }

}
