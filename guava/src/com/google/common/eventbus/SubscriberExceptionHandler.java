package com.google.common.eventbus;

/**
 * 事件订阅者抛出的异常处理器接口
 *
 * @since 16.0
 */
@ElementTypesAreNonnullByDefault
public interface SubscriberExceptionHandler {
  /** Handles exceptions thrown by subscribers. */
  void handleException(Throwable exception, SubscriberExceptionContext context);
}
