package com.google.common.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event subscriber method as being thread-safe. This annotation indicates that EventBus
 * may invoke the event subscriber simultaneously from multiple threads.
 *
 * <p>This does not mark the method, and so should be used in combination with {@link Subscribe}.
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ElementTypesAreNonnullByDefault
public @interface AllowConcurrentEvents {}
