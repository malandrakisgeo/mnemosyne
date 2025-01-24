package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

/**
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({METHOD})
@Repeatable(UpdatesCaches.class)
public @interface UpdatesCache {

    /**
     * Name of the target cache.
     * <p>
     * If no cache with the given name is found, the update step is skipped with no errors or warnings.
     */
    String name();

    /**
     * The names of the keys annotated as @UpdateKey that are to be used as keys for the particular cache to be updated.
     * The same method may update multiple caches at the same time, but some of the updateKeys may not be of interest for all
     * caches, hence the need for this array.
     * <p>
     * <b>Important: </b> the order of the keys has to be accurate. If you only use keys annotated as @UpdateKeys to update a cache,
     * you must give their names in the same order they are used as arguments in the underlying method.
     * And if you use a combination of annotatedKeys and targetObjectKeys, you must specify the ordering in the keyOrder (see below).
     * <p>
     * Not doing so may not lead to exceptions or even warnings, and may only lead to "silent" errors in what the caches return.
     * <p>
     * Example:
     * <pre>
     * {@code
     *
     * @Cached(name="getTransactionsByUser")
     * public List<Transaction> getTransactionsByUser(String userId, boolean pending);
     *
     * @Cached(name="getTransactionByUUID")
     * public Transaction unrelatedCache(UUID uuid);
     *
     * @UpdatesCache(name="getTransactionByUUID", annotatedKeys={"uuid"})
     * @UpdatesCache(name="getTransactionsByUser", annotatedKeys={"userId", "pending"}) //Could lead to a class-cast exception if it was {"pending", "userId"}, and would lead to silent cache discrepancies if both were Strings in the method!
     * public Transaction createTransactionForUser(@UpdateKey(name="userId") String userId, @UpdateKey(name="pending") boolean pending, @UpdateKey UUID uuid)
     *
     * }
     * </pre>
     */
    String[] annotatedKeys() default "";

    /**
     * The names of the key fields present in the target object, i.e. the object that is used for the update (either annotated as @UpdatedValue, or just what the Method returns if an @UpdatedValue is not present).
     * <p>
     * The fields must either be directly accessible (e.g. public), or have a getter that follows Java naming conventions.
     * <p>
     * As with the annotatedKeys, the field names here <b>must</b> be in the same ordering as the arguments in the target cached Method.
     * <p>
     * If a combination of annotatedKeys and targetObjectKeys is used, the order of the keys shall be specified in the keyOrder field.
     */

    String[] targetObjectKeys() default "";

    /**
     * The ordering of arguments in a method under update.
     * Used only if a combination of annotatedKeys and targetObjectKeys is in use, otherwise ignored.
     */
    String[] keyOrder() default "";

    /**
     * Determines whether the purpose is to remove values from the cache or not, and how. See RemoveNode's documentation for details.
     * You can limit its use by setting one or more conditions on conditionalDelete()
     */
    RemoveMode removeMode() default RemoveMode.NONE;

    /**
     * Refers to one or more boolean values that have to be true before adding something new to the cache.
     * These values are either annotated as @UpdateKey with the given name, or are present as fields in the
     * target object (i.e. the object annotated with @UpdatedValue or returned from the function, if an @UpdatedValue is absent).
     * <p>
     * Multiple booleans result to a logical AND. If the keys cannot be cast to boolean, an exception is thrown.
     * <p>
     * Booleans we want to be negative must start with an exclamation mark ('!').
     * <p>
     * Example:
     * <pre>
     *       {@code
     *       @UpdatesCache(name="getActiveUsers", conditionalAdd={"isActivated"}, conditionalRemove={"!isActivated"})
     *       public User saveUserDetails( String email, @UpdateKey(name="isActivated") boolean isActivated)
     *
     *       }
     *       </pre>
     */
    String[] conditionalAdd() default "";

    String[] conditionalDelete() default "";


    enum RemoveMode {
        /**
         * No removals (default)
         */
        NONE,
        /**
         * Removes a key from the cache.
         * In the default mnemosyne cache algorithms, this means that the keys are deleted, and the number of uses for associated IDs
         * is decreased. If it is zero, the ValuePool is informed that a cache no longer uses one or more IDs at all.
         * It is then up to the valuePool to decide whether the IDs and respective objects are kept or removed from it too.
         */
        REMOVE_KEY,
        /**
         * Removes a particular ID from one collection only. A key is necessary for this to happen.
         * May only be used with caches that return a collection, otherwise ignored.
         */
        REMOVE_VALUE_FROM_SINGLE_COLLECTION,
        /**
         * Removes a particular ID from all available collections.
         * No key is necessary for this operation, and the ID is enough.
         * May only be used with caches that return a collection, otherwise ignored.
         */
        REMOVE_VALUE_FROM_ALL_COLLECTIONS,

        /**
         * Invalidates cache completely.
         */
        INVALIDATE_CACHE
    }

}
