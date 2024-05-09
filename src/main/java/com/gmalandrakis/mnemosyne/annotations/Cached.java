package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.cache.FIFOCache;
import com.gmalandrakis.mnemosyne.core.MnemoProxy;

/**
 * The presence of the annotation leads to the creation of a {@link MnemoProxy @MnemoProxy}.
 * <p>
 * When applied to a method, all arguments are considered as a key, unless one of them
 * is annotated as {@link com.gmalandrakis.mnemosyne.annotations.Key @Key}.
 * <p>
 * When using any implementation of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache @AbstractGenericCache}, all of
 * the fields here are utilized. Otherwise it depends on the specific implementation
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface Cached {

    /**
     * @return The name of the cache in use.
     */
    String cacheName() default "";


    /**
     * Determines whether values expire X milliseconds after the last access (default)
     * or after creation/update.
     */
    boolean countdownFromCreation() default false;

    /**
     * Defines the cache algorithm.
     */
    Class<? extends AbstractMnemosyneCache> cacheType() default FIFOCache.class;

    /**
     * The TTL (Time To Live) in milliseconds, i.e. the time after which the value is no longer relevant.
     * <p>
     * By default, there is no TTL, which means that the cache entries
     * either live as long as the program, or have lifetimes depending on the algorithm
     * itself (e.g. the oldest FIFO entry "lives" as long as the queue is not full).
     * <p>
     * In implementations of AbstractGenericCache, zero and negative values translate to Long.MAX_VALUE.
     *
     * @return The expiration time of the values in milliseconds.
     */
    long timeToLive() default 0;

    /**
     * Time interval between calls to the evictAll() function of the EvictionAlgorithm in milliseconds.
     * The countdown starts on Cache initialization.
     * Recommended if neither capacity(), nor timeToLive(), are set.
     * <p>
     * In implementations of AbstractGenericCache, zero and negative values translate to Long.MAX_VALUE.
     */
    long invalidationInterval() default 0;


    /**
     * The maximum number of entries in the cache.
     * <p>
     * The use of this value is up to the implementation of the AbstractMnemosyneCache.
     * <p>
     * By default, there is no capacity, and that means that all entries are kept in memory
     * as long as the program runs unless evicted by other mechanisms.
     * <p>
     * In implementations of AbstractGenericCache, zero and negative values translate to Integer.MAX_VALUE.
     */
    int capacity() default 0;


    /**
     * Defines the number of available threads in the internal ThreadPool of the cache.
     * If not set, a CachedThreadPool is used for instances of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}.
     */
    int threadPoolSize() default 0;

    /**
     * Evict preemptively if the size of the cache exceeds a certain percentage of the total capacity.
     * <p>
     * Depending on the size of the cache, the complexity of the algorithm, and the number of threads concurrently writing on it,
     * it can take time to compute the values that should be evicted next, and even the actual eviction can be time-consuming.
     * It may be prudent to start the procedure before the size reaches 100% of the capacity.
     * <p>
     * The use of this value is up to the implementation of the AbstractMnemosyneCache.
     * <p>
     * In implementations of AbstractGenericCache, values over 100 or negative are switched to 80. An internal thread periodically checks
     * the current size of the cache and evicts if the percentage of the size compared to total capacity is equal or larger than this.
     * A value of 100 means that only capacity is taken into account.
     */
    short preemptiveEvictionPercentage() default 80;


    /**
     * The percentage of non-expired entries to be removed when the cache is full.
     * <p>
     * In implementations of AbstractGenericCache, a zero value (not recommended) means that exactly one non-expired entry is removed
     * on every eviction. Values over 100 and negative are regarded as zero.
     */
    short evictionStepPercentage() default 15;

}
