package com.gmalandrakis.mnemosyne.annotations;

import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.cache.FIFOCache;
import com.gmalandrakis.mnemosyne.core.MnemoProxy;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The presence of the annotation leads to the creation of a {@link MnemoProxy MnemoProxy}.
 * <p>
 * When applied to a method, all arguments are considered as a key, unless one of them
 * is annotated as {@link com.gmalandrakis.mnemosyne.annotations.Key @Key}.
 * <p>
 * When using any implementation of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, all of
 * the fields here are utilized. Otherwise it depends on the specific implementation
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
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
     * The countdown starts either on creation, or on the most recent access, depending on the value of {@link com.gmalandrakis.mnemosyne.annotations.Cached#countdownFromCreation}.
     * <p>
     * By default, there is no TTL, which means that the cache entries
     * either live as long as the program, or have lifetimes depending on the algorithm
     * itself (e.g. the oldest FIFO entry "lives" as long as the queue is not full).
     * <p>
     * In implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, zero and negative values are ignored.
     * Otherwise, the value defines the interval between automated evictions
     * -purging the expired values periodically.
     *
     * @return The expiration time of the values in milliseconds.
     */
    long timeToLive() default 0;

    /**
     * Time interval between evictions in milliseconds.
     * Recommended if neither capacity(), nor timeToLive(), are set.
     * <p>
     * In implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, zero and negative values are ignored.
     */
    long invalidationInterval() default 0;


    /**
     * The maximum number of entries in the cache.
     * <p>
     * The use of this value is up to the implementation of the AbstractMnemosyneCache.
     * <p>
     * By default, there is no capacity, and that means that all entries are kept in memory
     * as long as the program runs unless evicted by other mechanisms (e.g. expiration check).
     * <p>
     * In implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, zero and negative values are ignored.
     */
    int capacity() default 0;


    /**
     * Defines the number of available threads in the internal ThreadPool of the cache.
     * For instances of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, a CachedThreadPool is used when not set.
     */
    int threadPoolSize() default 0;

    /**
     * Evict preemptively if the size of the cache exceeds a certain percentage of the total capacity.
     * <p>
     * Depending on the size of the cache, the complexity of the algorithm, and the number of threads concurrently writing on it,
     * starting the eviction only after the total capacity is reached, can be problematic.
     * It may be prudent to start the procedure before the cache size reaches 100% of the capacity.
     * <p>
     * The use of this value is up to the implementation of the AbstractMnemosyneCache.
     * <p>
     * In implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, values over 100 or negative are switched to 80. An internal thread periodically checks
     * the current size of the cache and starts evicting once the percentage of the size compared to total capacity is equal or larger than this.
     * A value of 0 or 100 means that only the total capacity is taken into account.
     */
    short preemptiveEvictionPercentage() default 0;


    /**
     * The percentage of non-expired entries to be removed when the cache is full.
     * <p>
     * In implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache}, a zero value (not recommended) means that exactly one non-expired entry is removed
     * on every eviction. Values over 100 and negative are regarded as zero.
     */
    short evictionStepPercentage() default 5;

}
