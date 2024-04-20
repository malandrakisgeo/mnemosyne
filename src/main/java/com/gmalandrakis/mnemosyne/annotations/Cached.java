package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.gmalandrakis.mnemosyne.cache.AbstractCache;
import com.gmalandrakis.mnemosyne.cache.FIFOCache;
import com.gmalandrakis.mnemosyne.core.MnemoProxy;

/**
 * The presence of the annotation leads to the creation of a {@link MnemoProxy @MnemoProxy}.
 * <p>
 * When set on a method, all arguments are regarded as a key, unless one of them
 * is annotated as {@link com.gmalandrakis.mnemosyne.annotations.Key @Key}.
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface Cached {

    /**
     * Mnymosyne uses the value defined here to connect the method or type to an implementation of {@link AbstractCache}
     * that has been defined in the cache service.
     * <p>
     * If the name does not correspond to an existing Cache, a Runtime exception will be thrown.
     *
     * @return The name of the cache in use.
     */
    String cacheName() default "";

    /**
     * Many cache algorithms take into account a TTL (Time To Live) value, i.e. when the value is no longer relevant.
     * <p>
     * By default, there is no TTL, which means that the cache entries
     * either live as long as the program, or have lifetimes depending on the algorithm
     * itself (e.g. the oldest FIFO entry "lives" as long as the cache is not full).
     * <p>
     * Negative values translate to Long.MAX_VALUE.
     *
     * @return The expiration time of the values in milliseconds.
     */
    long timeToLive() default 0;

    /**
     * The maximum number of entries in the cache.
     * <p>
     * If the value is non-zero, a cache-specific thread checks periodically if the Cache is full and
     * calls evict().
     * <p>
     * By default, there is no capacity, and that means that all entries are kept in memory
     * as long as the program runs unless evicted by other mechanisms.
     * <p>
     * Negative values translate to Long.MAX_VALUE.
     *
     * @return
     */
    long capacity() default 0;

    /**
     * Time interval between calls to the evictAll() function of the EvictionAlgorithm in milliseconds.
     * The countdown starts on Cache creation (i.e. when the application is started).
     * Recommended if neither capacity(), nor expiration(), are set.
     * <p>
     * Negative values translate to Long.MAX_VALUE.
     */
    long forcedEvictionInterval() default 0;


    /**
     * Determines whether the values expire X milliseconds after the last access (default)
     * or after creation/update.
     */
    boolean countdownFromCreation() default false;

    Class<? extends AbstractCache> cacheType() default FIFOCache.class;

}
