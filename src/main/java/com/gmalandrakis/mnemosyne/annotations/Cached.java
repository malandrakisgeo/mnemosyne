package com.gmalandrakis.mnemosyne.annotations;

import com.gmalandrakis.mnemosyne.cache.AbstractGenericCache;
import com.gmalandrakis.mnemosyne.cache.AbstractMnemosyneCache;
import com.gmalandrakis.mnemosyne.cache.FIFOCache;
import com.gmalandrakis.mnemosyne.core.MnemoProxy;
import com.gmalandrakis.mnemosyne.structures.AddMode;
import com.gmalandrakis.mnemosyne.structures.RemoveMode;

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
 * When using any implementation of {@link AbstractGenericCache AbstractGenericCache}, all of
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
    String cacheName();


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
     * In implementations of {@link AbstractGenericCache AbstractGenericCache}, zero and negative values are ignored.
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
     * In implementations of {@link AbstractGenericCache AbstractGenericCache}, zero and negative values are ignored.
     */
    long invalidationInterval() default 0;


    /**
     * The maximum number of entries in the cache.
     * <p>
     * The use of this value is up to the implementation of the AbstractMnemosyneCache. Depending on the implementation, it may set a limit on
     * the IDs/values saved, or a limit on the keys independently of how many IDs/values they are associated with, or a limit on both.
     * <p>
     * By default, there is no capacity, and that means that all entries are kept in memory
     * as long as the program runs unless evicted by other mechanisms (e.g. expiration check, manual invalidation, removal via an @UpdatesCache, etc).
     * <p>
     * In implementations of {@link AbstractGenericCache AbstractGenericCache}, this refers to the maximum number of keys
     * allowed in memory, and zero or negative values are ignored.
     */
    int capacity() default 0;


    /**
     * Defines the number of available threads in the internal ThreadPool of the cache.
     * <p>
     * The threads are used by both the cache and the  {@link com.gmalandrakis.mnemosyne.core.MnemoProxy MnemoProxy} using it.
     * For instances of {@link AbstractGenericCache AbstractGenericCache}, a CachedThreadPool is used when not set
     * or set as less than 5.
     */
    int threadPoolSize() default 0;

    /**
     * Evict preemptively if the size of the cache exceeds a certain percentage of the total capacity.
     * <p>
     * Depending on the size of the cache, the complexity of the algorithm, and the number of threads concurrently writing on it,
     * it may be problematic to start the eviction only after the total capacity is reached. It can take time to determine the values
     * to be evicted, as well as to remove them from all related structures. It may be prudent to start the procedure before the cache size reaches 100% of the capacity.
     * <p>
     * The use of this value is up to the implementation of the AbstractMnemosyneCache.
     * <p>
     * In implementations of {@link AbstractGenericCache AbstractGenericCache}, values over 100 or negative are switched to 100. An internal thread periodically checks
     * the current size of the cache and starts evicting once the percentage of the size compared to total capacity is equal or larger than this.
     * A value of 0 or 100 means that only the total capacity is taken into account.
     */
    short preemptiveEvictionPercentage() default 0;


    /**
     * The percentage of non-expired entries to be removed when the cache is full.
     * <p>
     * In implementations of {@link AbstractGenericCache AbstractGenericCache}, a zero value (not recommended) means that exactly one non-expired entry is removed
     * on every eviction. Values over 100 and negative are regarded as zero.
     */
    short evictionStepPercentage() default 5;

    /**
     * Experimental and not for general use.
     * <p>
     * If a Method with a 1-1 correspondence between keys and values uses a Collection, List, or Set of keys as argument,
     * and returns a Collection, List,or Set of values as a result, setting this to true will enable special handling for it.
     * <p>
     * Whenever the Method is called with a Collection of keys, Mnemosyne will:<br>
     * 1. Check which keys already exist in the cache<br>
     * 2. Asynchronously call the Method with every key that did not exist in the cache separately, and create a map of keys and values.<br>
     * 3. Store the result in the cache<br>
     * 4. Return a combination of existent and the new values.<br>
     * Can work only for methods that return an abstract Collection, List, or Set (i.e. will not work with Methods that
     * return concrete implementations of the aforementioned, like ArrayList or HashSet), with a 1-1 correspondence
     * between keys and values.
     * <p>
     * Setting to true can make your cache more effective in the long-term, but may make it slower in the beginning.
     * Should not be set to true if the underlying method calls a pay-per-request service.
     * Setting to true is not recommended unless necessary,
     * and <b>strongly discouraged</b> in cases where the length and/or element order of the collection used as argument
     * or returned plays any role (e.g. Collection of XY coordinates).
     */
    boolean allowSeparateHandlingForKeyCollections() default false;

    /**
     * The names of the key fields present in the target object, i.e. the object that is used for the update (either annotated as @UpdatedValue, or just what the Method returns if an @UpdatedValue is not present).
     * <p>
     * The fields must either be directly accessible (e.g. public), or have a getter that follows Java naming conventions.
     * <p>
     * As with the annotatedKeys, the field names here <b>must</b> be in the same ordering as the arguments in the target cached Method.
     * <p>
     * If a combination of annotatedKeys and targetObjectKeys is used, the order of the keys shall be specified in the keyOrder field.
     * <p>
     * Note that in the @Cached context, it is used only if an @UpdatesValuePool for the same type is present and ignored otherwise.
     */

    String[] targetObjectKeys() default "";

    /**
     * Determines how new values are added to the existing caches.
     * Refer to {@link AddMode} for further details.
     *
     */
    AddMode addMode();

    /**
     * Determines how new values are removed from the existing caches.
     * Refer to {@link RemoveMode} for further details.
     */
    RemoveMode removeMode();

    /**
     * Refers to one or more boolean values that have to be true before adding something new to the cache.
     * These values are either annotated as @UpdateKey with the given name, or are present as fields in the
     * target object (i.e. the object annotated with @UpdatedValue or returned from the function, if an @UpdatedValue is absent).
     * <p>
     * By default, if multiple conditions are present, a logical AND applies (i.e. all have to be true). You may set conditionalANDGate to false if you prefer a logical OR.
     * <p>
     * If the keys cannot be cast to boolean, an exception is thrown.
     * <p>
     * If the result of the addOnCondition operation clashes with the one for the conditionalDelete, an exception is thrown.
     * <p>
     * Booleans we want to be negative must start with an exclamation mark ('!').
     * <p>
     * Example:
     * <pre>
     *       {@code
     *       @UpdatesCache(name="getActiveUsers", addOnCondition={"isActive", "isVerified"}, removeOnCondition={"!isActive"})
     *       public void saveUserDetails(@UpdatedValue User newUser)
     *       }
     *       </pre>
     * Set complementaryCondition to true if you expect a removal when the condition is false and the object is present in the cache.
     */
    String[] addOnCondition() default "";

    /**
     * Similar to addOnCondition.
     * Set complementaryCondition to true if you expect an addition when the condition is false and the object is present in the cache.
     */
    String[] removeOnCondition() default "";

    /**
     * AddOnCondition is often complementary to removeOnCondition (i.e. "add to cache if A"  often implies "evict from cache if not A"), and vice versa.
     * Setting complementaryCondition treats the inverse of the given condition for adding as a condition for removing, and vice versa.
     * Example:
     * <pre>
     *       {@code
     *       @UpdatesCache(name="getActiveUsers", addOnCondition={"isActive", "isVerified"}, conditionalANDGate = false, complementaryCondition = true) //removes from cache if isActive and isVerified are both false
     *       public void saveActiveUserDetails(@UpdatedValue User newUser)
     *       }
     *       </pre>
     */
    boolean complementaryCondition() default false;

    /**
     * If set to true, all conditions in the addOnCondition and removeOnCondition have to be true in order for the new element to be added/removed.
     * Otherwise one suffices.
     */
    boolean conditionalANDGate() default true;


}
