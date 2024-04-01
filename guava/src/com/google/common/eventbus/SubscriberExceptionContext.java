package com.google.common.eventbus;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Method;

/**
 * 订阅者消费执行事件抛出的异常上下文
 *
 * @since 16.0
 */
@ElementTypesAreNonnullByDefault
public class SubscriberExceptionContext {

  /** 事件总线、事件、事件订阅者、事件订阅者方法 */
  private final EventBus eventBus;
  private final Object event;
  private final Object subscriber;
  private final Method subscriberMethod;
  public EventBus getEventBus() {
    return eventBus;
  }
  public Object getEvent() {
    return event;
  }
  public Object getSubscriber() {
    return subscriber;
  }
  public Method getSubscriberMethod() {
    return subscriberMethod;
  }

  /** 构造方法 */
  SubscriberExceptionContext(
      EventBus eventBus, Object event, Object subscriber, Method subscriberMethod) {
    this.eventBus = checkNotNull(eventBus);
    this.event = checkNotNull(event);
    this.subscriber = checkNotNull(subscriber);
    this.subscriberMethod = checkNotNull(subscriberMethod);
  }

}
