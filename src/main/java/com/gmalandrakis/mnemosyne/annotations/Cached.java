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
     * Mnemosyne uses the value defined here to connect the method or type to an implementation of {@link AbstractCache}
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
     * Zero and negative values to Long.MAX_VALUE.
     *
     * @return The expiration time of the values in milliseconds.
     */
    long timeToLive() default 0;

    /**
     * The maximum number of entries in the cache.
     * <p>
     * The use of this value is up to the implementation of the AbstractCache.
     * E.g. in an AbstractGenericCache, if the value is non-zero, an internal thread checks periodically the size of the cache and
     * calls evict() if needed.
     * <p>
     * By default, there is no capacity, and that means that all entries are kept in memory
     * as long as the program runs unless evicted by other mechanisms.
     * <p>
     * Zero and negative values translate to Integer.MAX_VALUE.
     *
     * @return
     */
    int capacity() default 0;

    /**
     * Time interval between calls to the evictAll() function of the EvictionAlgorithm in milliseconds.
     * The countdown starts on Cache initialization.
     * Recommended if neither capacity(), nor timeToLive(), are set.
     * <p>
     * Zero and negative values translate to Long.MAX_VALUE.
     */
    long invalidationInterval() default 0;

    /**
     * Determines whether the values expire X milliseconds after the last access (default)
     * or after creation/update.
     */
    boolean countdownFromCreation() default false;

    Class<? extends AbstractCache> cacheType() default FIFOCache.class;

    /**
     * Defines the number of available threads in the internal ThreadPool of the cache.
     * If not set, a CachedThreadPool is used for instances of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}.
     */
    int threadPoolSize() default 0;

    /**
     * Evict preemptively if the size of the cache exceeds this percentage of the total capacity.
     * <p>
     * Depending on the size of the cache, the complexity of the algorithm, and the number of threads writing concurrently on it,
     * it can take time to compute the values that should be evicted next, and even the actual eviction can be time-consuming.
     * Is may be prudent to start the procedure before the size reaches 100% of the capacity.
     * <p>
     * The use of this value is up to the implementation of the AbstractCache.
     * <p>
     * If set for implementations of AbstractGenericCache, an internal thread periodically checks
     * the current size of the cache and evicts if the percentage of the size compared to total capacity is more than this.
     * <p>
     * Values over 100 or negative are zeroed.
     */
    short preemptiveEvictionPercentage() default 0;

}
