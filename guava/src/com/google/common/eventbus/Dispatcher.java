package com.google.common.eventbus;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Queues;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 事件分发器
 *
 * @author Colin Decker
 */
@ElementTypesAreNonnullByDefault
abstract class Dispatcher {

  /** 分发某个事件给订阅者列表执行 */
  abstract void dispatch(Object event, Iterator<Subscriber> subscribers);

  /** 1）线程级队列事件分发器：在每个线程中维护一个事件队列，确保事件执行的顺序性 */
  static Dispatcher perThreadDispatchQueue() {
    return new PerThreadQueuedDispatcher();
  }
  private static final class PerThreadQueuedDispatcher extends Dispatcher {

    /** 线程级待分发事件队列（初始化为空队列）、线程级分发状态（初始化为false） */
    private final ThreadLocal<Queue<Event>> queue = ThreadLocal.withInitial(Queues::newArrayDeque);
    private final ThreadLocal<Boolean> dispatching = ThreadLocal.withInitial(() -> false);

    @Override
    void dispatch(Object event, Iterator<Subscriber> subscribers) {
      checkNotNull(event);
      checkNotNull(subscribers);
      // 入队
      Queue<Event> queueForThread = queue.get();
      queueForThread.offer(new Event(event, subscribers));
      // 分发事件
      if (!dispatching.get()) {
        dispatching.set(true);
        try {
          // 自旋获取队列中的事件，并遍历订阅者列表分发事件执行
          Event nextEvent;
          while ((nextEvent = queueForThread.poll()) != null) {
            while (nextEvent.subscribers.hasNext()) {
              nextEvent.subscribers.next().dispatchEvent(nextEvent.event);
            }
          }
        } finally {
          // 清空队列
          dispatching.remove();
          queue.remove();
        }
      }
    }

    /** 事件包装类：原始事件&订阅者列表 */
    private static final class Event {
      private final Object event;
      private final Iterator<Subscriber> subscribers;
      private Event(Object event, Iterator<Subscriber> subscribers) {
        this.event = event;
        this.subscribers = subscribers;
      }
    }

  }

  /** 2）全局队列事件分发器 */
  static Dispatcher legacyAsync() {
    return new LegacyAsyncDispatcher();
  }
  private static final class LegacyAsyncDispatcher extends Dispatcher {

    /** 全局队列事件（初始化为空队列） */
    private final ConcurrentLinkedQueue<EventWithSubscriber> queue = Queues.newConcurrentLinkedQueue();

    @Override
    void dispatch(Object event, Iterator<Subscriber> subscribers) {
      checkNotNull(event);
      // 入队
      while (subscribers.hasNext()) {
        queue.add(new EventWithSubscriber(event, subscribers.next()));
      }
      // 出队，并分发事件执行
      EventWithSubscriber e;
      while ((e = queue.poll()) != null) {
        e.subscriber.dispatchEvent(e.event);
      }
    }

    /** 事件包装类：原始事件&订阅者 */
    private static final class EventWithSubscriber {
      private final Object event;
      private final Subscriber subscriber;

      private EventWithSubscriber(Object event, Subscriber subscriber) {
        this.event = event;
        this.subscriber = subscriber;
      }
    }

  }

  /** 3）立即执行事件分发器：无队列 */
  static Dispatcher immediate() {
    return ImmediateDispatcher.INSTANCE;
  }
  private static final class ImmediateDispatcher extends Dispatcher {
    private static final ImmediateDispatcher INSTANCE = new ImmediateDispatcher();
    @Override
    void dispatch(Object event, Iterator<Subscriber> subscribers) {
      // 直接遍历订阅者列表分发事件执行
      checkNotNull(event);
      while (subscribers.hasNext()) {
        subscribers.next().dispatchEvent(event);
      }
    }
  }

}
