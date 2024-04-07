package com.google.common.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;
import java.util.AbstractMap.SimpleImmutableEntry;
import javax.annotation.CheckForNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * 缓存项移除通知：GC后K-V可能为null
 *
 * @author Charles Fry
 * @since 10.0
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
public final class RemovalNotification<K, V>
    extends SimpleImmutableEntry<@Nullable K, @Nullable V> {
  private static final long serialVersionUID = 0;

  /** 移除原因 */
  private final RemovalCause cause;
  public RemovalCause getCause() {
    return cause;
  }
  public boolean wasEvicted() {
    return cause.wasEvicted();
  }

  /** 构造方法 */
  public static <K, V> RemovalNotification<K, V> create(
      @CheckForNull K key, @CheckForNull V value, RemovalCause cause) {
    return new RemovalNotification<>(key, value, cause);
  }
  private RemovalNotification(@CheckForNull K key, @CheckForNull V value, RemovalCause cause) {
    super(key, value);
    this.cause = checkNotNull(cause);
  }

}
