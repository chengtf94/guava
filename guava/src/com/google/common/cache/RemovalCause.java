package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;

/**
 * 缓存项移除原因
 *
 * @author Charles Fry
 * @since 10.0
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
public enum RemovalCause {

  /** 用户主动移除：例如invalidate等 */
  EXPLICIT {
    @Override
    boolean wasEvicted() {
      return false;
    }
  },

  /** 用户主动移除：例如refresh等 */
  REPLACED {
    @Override
    boolean wasEvicted() {
      return false;
    }
  },

  /** GC自动移除：例如弱引用、软引用等 */
  COLLECTED {
    @Override
    boolean wasEvicted() {
      return true;
    }
  },

  /** 过期自动移除：例如expireAfterAccess、expireAfterWrite等 */
  EXPIRED {
    @Override
    boolean wasEvicted() {
      return true;
    }
  },

  /** 容量自动移除：例如maximumSize、maximumWeight等 */
  SIZE {
    @Override
    boolean wasEvicted() {
      return true;
    }
  };

  /** 是否被自动移除 */
  abstract boolean wasEvicted();

}
