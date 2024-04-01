package com.google.common.eventbus;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;

/**
 * 死亡事件（指没有订阅者的事件）
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@ElementTypesAreNonnullByDefault
public class DeadEvent {

  /** 事件总线、原始事件 */
  private final Object source;
  private final Object event;
  public Object getSource() {
    return source;
  }
  public Object getEvent() {
    return event;
  }

  /** 构造方法 */
  public DeadEvent(Object source, Object event) {
    this.source = checkNotNull(source);
    this.event = checkNotNull(event);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("source", source).add("event", event).toString();
  }

}
