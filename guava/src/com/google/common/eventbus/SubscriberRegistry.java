package com.google.common.eventbus;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.j2objc.annotations.Weak;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.CheckForNull;

/**
 * 订阅者注册中心
 *
 * @author Colin Decker
 */
@ElementTypesAreNonnullByDefault
final class SubscriberRegistry {

  /** 订阅者注册Map：key为事件类，value为该事件的订阅者集合 */
  private final ConcurrentMap<Class<?>, CopyOnWriteArraySet<Subscriber>> subscribers = Maps.newConcurrentMap();

  /** 订阅者注册中心归属的事件总线 */
  @Weak private final EventBus bus;

  /** 构造方法 */
  SubscriberRegistry(EventBus bus) {
    this.bus = checkNotNull(bus);
  }

  /** 注册某个事件监听器对象中的所有订阅者方法（一个监听器类中可包含多个订阅者方法） */
  void register(Object listener) {
    // 获取某个事件监听器对象中的所有订阅者方法
    Multimap<Class<?>, Subscriber> listenerMethods = findAllSubscribers(listener);
    // 遍历注册
    for (Entry<Class<?>, Collection<Subscriber>> entry : listenerMethods.asMap().entrySet()) {
      Class<?> eventType = entry.getKey();
      Collection<Subscriber> eventMethodsInListener = entry.getValue();
      CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(eventType);
      if (eventSubscribers == null) {
        CopyOnWriteArraySet<Subscriber> newSet = new CopyOnWriteArraySet<>();
        eventSubscribers = MoreObjects.firstNonNull(subscribers.putIfAbsent(eventType, newSet), newSet);
      }
      eventSubscribers.addAll(eventMethodsInListener);
    }
  }

  /** 卸载某个事件监听器对象中的所有订阅者方法（一个监听器类中可包含多个订阅者方法） */
  void unregister(Object listener) {
    // 获取某个事件监听器对象中的所有订阅者方法
    Multimap<Class<?>, Subscriber> listenerMethods = findAllSubscribers(listener);
    // 遍历卸载
    for (Entry<Class<?>, Collection<Subscriber>> entry : listenerMethods.asMap().entrySet()) {
      Class<?> eventType = entry.getKey();
      Collection<Subscriber> listenerMethodsForType = entry.getValue();
      CopyOnWriteArraySet<Subscriber> currentSubscribers = subscribers.get(eventType);
      if (currentSubscribers == null || !currentSubscribers.removeAll(listenerMethodsForType)) {
        throw new IllegalArgumentException(
            "missing event subscriber for an annotated method. Is " + listener + " registered?");
      }
    }
  }

  /** 获取某个事件监听器对象中的所有订阅者方法：key为事件类，value为该事件的订阅者 */
  private Multimap<Class<?>, Subscriber> findAllSubscribers(Object listener) {
    Multimap<Class<?>, Subscriber> methodsInListener = HashMultimap.create();
    Class<?> clazz = listener.getClass();
    for (Method method : getAnnotatedMethods(clazz)) {
      Class<?>[] parameterTypes = method.getParameterTypes();
      Class<?> eventType = parameterTypes[0];
      methodsInListener.put(eventType, Subscriber.create(bus, listener, method));
    }
    return methodsInListener;
  }

  /** 获取某个事件监听器类中带有Subscribe注解的方法列表：基于本地缓存GuavaCache */
  private static ImmutableList<Method> getAnnotatedMethods(Class<?> clazz) {
    try {
      return subscriberMethodsCache.getUnchecked(clazz);
    } catch (UncheckedExecutionException e) {
      throwIfUnchecked(e.getCause());
      throw e;
    }
  }

  /** 本地缓存：key为事件监听器类，value为该类中带有Subscribe注解的方法列表
   */
  private static final LoadingCache<Class<?>, ImmutableList<Method>> subscriberMethodsCache =
          CacheBuilder.newBuilder()
                  .weakKeys()
                  .build(
                          new CacheLoader<Class<?>, ImmutableList<Method>>() {
                            @Override
                            public ImmutableList<Method> load(Class<?> concreteClass) throws Exception {
                              return getAnnotatedMethodsNotCached(concreteClass);
                            }
                          });

  /** 获取某个事件监听器类中带有Subscribe注解的方法列表：未缓存 */
  private static ImmutableList<Method> getAnnotatedMethodsNotCached(Class<?> clazz) {
    Set<? extends Class<?>> supertypes = TypeToken.of(clazz).getTypes().rawTypes();
    Map<MethodIdentifier, Method> identifiers = Maps.newHashMap();
    for (Class<?> supertype : supertypes) {
      for (Method method : supertype.getDeclaredMethods()) {
        /** 使用Subscribe注解标注*/
        if (method.isAnnotationPresent(Subscribe.class) && !method.isSynthetic()) {
          // TODO(cgdecker): Should check for a generic parameter type and error out
          Class<?>[] parameterTypes = method.getParameterTypes();
          checkArgument(
              parameterTypes.length == 1,
              "Method %s has @Subscribe annotation but has %s parameters. "
                  + "Subscriber methods must have exactly 1 parameter.",
              method,
              parameterTypes.length);
          checkArgument(
              !parameterTypes[0].isPrimitive(),
              "@Subscribe method %s's parameter is %s. "
                  + "Subscriber methods cannot accept primitives. "
                  + "Consider changing the parameter to %s.",
              method,
              parameterTypes[0].getName(),
              Primitives.wrap(parameterTypes[0]).getSimpleName());
          MethodIdentifier ident = new MethodIdentifier(method);
          if (!identifiers.containsKey(ident)) {
            identifiers.put(ident, method);
          }
        }
      }
    }
    return ImmutableList.copyOf(identifiers.values());
  }

  /** 获取某个事件的所有订阅者 */
  Iterator<Subscriber> getSubscribers(Object event) {
    // 获取该事件的所有父类
    ImmutableSet<Class<?>> eventTypes = flattenHierarchy(event.getClass());
    // 遍历从订阅者注册中心获取订阅者
    List<Iterator<Subscriber>> subscriberIterators = Lists.newArrayListWithCapacity(eventTypes.size());
    for (Class<?> eventType : eventTypes) {
      CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(eventType);
      if (eventSubscribers != null) {
        subscriberIterators.add(eventSubscribers.iterator());
      }
    }
    return Iterators.concat(subscriberIterators.iterator());
  }

  @VisibleForTesting
  Set<Subscriber> getSubscribersForTesting(Class<?> eventType) {
    return MoreObjects.firstNonNull(subscribers.get(eventType), ImmutableSet.<Subscriber>of());
  }

  /** 获取某个事件类的所有父类 */
  @VisibleForTesting
  static ImmutableSet<Class<?>> flattenHierarchy(Class<?> concreteClass) {
    try {
      return flattenHierarchyCache.getUnchecked(concreteClass);
    } catch (UncheckedExecutionException e) {
      throw Throwables.propagate(e.getCause());
    }
  }

  /** 本地缓存：key为事件类，value为该类的父类集合 */
  private static final LoadingCache<Class<?>, ImmutableSet<Class<?>>> flattenHierarchyCache =
          CacheBuilder.newBuilder()
                  .weakKeys()
                  .build(
                          new CacheLoader<Class<?>, ImmutableSet<Class<?>>>() {
                            // <Class<?>> is actually needed to compile
                            @SuppressWarnings("RedundantTypeArguments")
                            @Override
                            public ImmutableSet<Class<?>> load(Class<?> concreteClass) {
                              return ImmutableSet.<Class<?>>copyOf(
                                      TypeToken.of(concreteClass).getTypes().rawTypes());
                            }
                          });

  /** 订阅者方法唯一标识 */
  private static final class MethodIdentifier {

    private final String name;
    private final List<Class<?>> parameterTypes;

    MethodIdentifier(Method method) {
      this.name = method.getName();
      this.parameterTypes = Arrays.asList(method.getParameterTypes());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name, parameterTypes);
    }

    @Override
    public boolean equals(@CheckForNull Object o) {
      if (o instanceof MethodIdentifier) {
        MethodIdentifier ident = (MethodIdentifier) o;
        return name.equals(ident.name) && parameterTypes.equals(ident.parameterTypes);
      }
      return false;
    }
  }

}
