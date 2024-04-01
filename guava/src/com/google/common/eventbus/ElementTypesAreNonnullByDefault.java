package com.google.common.eventbus;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.annotations.GwtCompatible;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * 类属性&方法&参数非空注解
 */
@GwtCompatible
@Retention(RUNTIME)
@Target(TYPE)
@TypeQualifierDefault({FIELD, METHOD, PARAMETER})
@Nonnull
@interface ElementTypesAreNonnullByDefault {}
