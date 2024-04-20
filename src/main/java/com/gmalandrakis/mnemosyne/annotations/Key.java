package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Used for at most one argument in a {@link com.gmalandrakis.mnemosyne.annotations.Cached @Cached} function.
 * <p>
 * If more than one arguments are annotated as @Key, only the first one is used.
 * <p>
 * If absent, all arguments are assembled in a {@link com.gmalandrakis.mnemosyne.structures.CompoundKey CompoundKey}
 */
@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface Key {
}
