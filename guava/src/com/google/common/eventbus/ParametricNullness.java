package com.google.common.eventbus;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.annotations.GwtCompatible;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 参数非空注解
 */
@GwtCompatible
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
@javax.annotation.meta.TypeQualifierNickname
@javax.annotation.Nonnull(when = javax.annotation.meta.When.UNKNOWN)
@interface ParametricNullness {
}
