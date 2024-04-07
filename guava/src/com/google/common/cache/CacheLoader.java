package com.google.common.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 缓存加载器基类
 *
 * @author Charles Fry
 * @since 10.0
 */
@GwtCompatible(emulated = true)
@ElementTypesAreNonnullByDefault
public abstract class CacheLoader<K, V> {
  protected CacheLoader() {}

  /** 加载 */
  public abstract V load(K key) throws Exception;

  /** 重新加载 */
  @GwtIncompatible
  public ListenableFuture<V> reload(K key, V oldValue) throws Exception {
    checkNotNull(key);
    checkNotNull(oldValue);
    return Futures.immediateFuture(load(key));
  }

  /** 批量加载 */
  public Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
    throw new UnsupportedLoadingOperationException();
  }

  /** 基于Function获取缓存加载器 */
  public static <K, V> CacheLoader<K, V> from(Function<K, V> function) {
    return new FunctionToCacheLoader<>(function);
  }
  private static final class FunctionToCacheLoader<K, V> extends CacheLoader<K, V>
          implements Serializable {
    private static final long serialVersionUID = 0;

    /** 计算函数 */
    private final Function<K, V> computingFunction;

    public FunctionToCacheLoader(Function<K, V> computingFunction) {
      this.computingFunction = checkNotNull(computingFunction);
    }

    @Override
    public V load(K key) {
      return computingFunction.apply(checkNotNull(key));
    }

  }

  /** 基于Supplier获取缓存加载器 */
  public static <V> CacheLoader<Object, V> from(Supplier<V> supplier) {
    return new SupplierToCacheLoader<>(supplier);
  }
  private static final class SupplierToCacheLoader<V> extends CacheLoader<Object, V>
          implements Serializable {
    private static final long serialVersionUID = 0;

    /** 计算函数 */
    private final Supplier<V> computingSupplier;

    public SupplierToCacheLoader(Supplier<V> computingSupplier) {
      this.computingSupplier = checkNotNull(computingSupplier);
    }

    @Override
    public V load(Object key) {
      checkNotNull(key);
      return computingSupplier.get();
    }

  }

  /** 异步重新加载：要求传入自定义执行器，Executor + Futures */
  @GwtIncompatible
  public static <K, V> CacheLoader<K, V> asyncReloading(
      final CacheLoader<K, V> loader, final Executor executor) {
    checkNotNull(loader);
    checkNotNull(executor);
    return new CacheLoader<K, V>() {
      @Override
      public V load(K key) throws Exception {
        return loader.load(key);
      }

      @Override
      public ListenableFuture<V> reload(final K key, final V oldValue) {
        ListenableFutureTask<V> task =
            ListenableFutureTask.create(() -> loader.reload(key, oldValue).get());
        executor.execute(task);
        return task;
      }

      @Override
      public Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
        return loader.loadAll(keys);
      }
    };
  }

  /** 自定义异常 */
  public static final class UnsupportedLoadingOperationException
      extends UnsupportedOperationException {
    UnsupportedLoadingOperationException() {}
  }
  public static final class InvalidCacheLoadException extends RuntimeException {
    public InvalidCacheLoadException(String message) {
      super(message);
    }
  }

}
