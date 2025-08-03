package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

/**
 * Updates the value pool with the latest instance of an object.
 * <p> Can only be used on methods that are neither cached themselves, nor
 * annotated as @UpdatesCache. The values to be acted on are the ones returned
 * by the method, unless an argument annotated with @UpdatedValue is present.
 * <p> By default, this only updates values that are already cached. </p>
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({METHOD})
public @interface UpdatesValuePool {
    /**
     * If true, the values returned or annotated as @UpdatedValue are removed from the value pool
     * and all caches that contain them.
     */
    boolean remove() default false;

    /**
     * Adds a value to the pool even if no cache currently uses it.
     * <p>
     * <b>Values added in the pool this way can only be removed by methods annotated as @UpdatesValuePool(remove = true).
     * If no such methods exist, the value may remain in memory until the application terminates. </b> .
     * <p>Avoid using if not necessary.
     */
    boolean addIfAbsent() default false;
}
