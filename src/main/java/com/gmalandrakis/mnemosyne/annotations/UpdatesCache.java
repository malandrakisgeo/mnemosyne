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
     * Determines how new values are added to the existing caches.
     * Refer to {@link AddMode} for further details.
     */
    AddMode addMode() default AddMode.NONE;

    /**
     * Determines how new values are removed from the existing caches.
     * Refer to {@link RemoveMode} for further details.
     */
    RemoveMode removeMode() default RemoveMode.NONE;

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
     */
    String[] addOnCondition() default "";

    /**
     * Similar to addOnCondition.
     */
    String[] removeOnCondition() default "";

    /*
        TODO: Add a flag that, if set to true, complementary logic for AddOnCondition/removeOnCondition will be set.
        I.E., if the condition is false, then remove.
     */

    /**
     * If set to true, all conditions in the addOnCondition and removeOnCondition have to be true in order for the new element to be added/removed.
     * Otherwise one suffices.
     */
    boolean conditionalANDGate() default true;

    enum RemoveMode {
        /**
         * No removals
         */
        NONE,
        /**
         * Removes a key from the cache (or invalidates a cache if it corresponds to a function with no arguments).
         * In the default mnemosyne cache algorithms, this means that the keys are deleted, and the number of uses for associated IDs
         * is decreased. If it is zero, the ValuePool is informed that a cache no longer uses one or more IDs at all.
         * It is then up to the valuePool to decide whether the IDs and respective objects are kept or removed from it too.
         */
        DEFAULT,
        /**
         * Removes a particular ID from one collection only. A key is necessary for this to happen.
         * May only be used with caches that return a collection, otherwise ignored.
         */
        REMOVE_VALUE_FROM_COLLECTION,
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

    enum AddMode {
        /**
         * No additions.
         */
        NONE,
        /**
         * For single value caches.
         * <p>
         * If a value is updated, the previous key now references the newest object.
         * <p>
         * If a (non special-handling) collection cache contains no values for the particular key (e.g. the underlying method has never been called before),
         * the underlying method is called preemptively to avoid cache discrepancies.
         */
        DEFAULT,
        /**
         * For collection caches.
         * The new or updated values are added to the collection. A proper remove mode is needed to remove the outdated ones, if that is necessary.
         * <p>
         * A key is necessary for the operation, except for functions that take no arguments.
         * <p>
         * If a (non special-handling) cache contains no values for the particular key (e.g. the underlying method has never been called before),
         * the underlying method is called preemptively to avoid cache discrepancies.
         */
        ADD_VALUES_TO_COLLECTION,
        /**
         * For (non special-handling) collection caches. Adds a particular ID to all available collections in the cache.
         * <p>
         * The new or updated values are added to all cached collections and the value pool, and the current values remain unchanged.
         * A proper remove mode is needed to remove the outdated ones, if that is necessary.
         * <p>
         * No key is necessary for the operation, as the values are added to all collections.
         * <p>
         * The current values are assumed to be up-to-date and complete, so no preemptive adding is done.
         */
        ADD_VALUES_TO_ALL_COLLECTIONS,
        /**
         * For collection caches.
         * A key is necessary, and any existing collection for this key is removed to be replaced by another collection.
         * Logically equivalent a DEFAULT removal mode combined with ADD_VALUES_TO_COLLECTION
         */
        REPLACE_EXISTING_COLLECTION //key necessary
    }

}
