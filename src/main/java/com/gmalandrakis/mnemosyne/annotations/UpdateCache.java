package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UpdatesCaches.class)
public @interface UpdateCache {

    /**
     * Name of the target cache.
     * <p>
     * If no cache with the given name is found, the update step is skipped with no errors or warnings.
     */
    String name();

    /**
     * The keys will correspond to the number of objects used as keys in the target cache.
     * If no @Key is specified in the target cache, the number of keys will correspond to the number of the arguments, otherwise it does not work.
     * May be empty for cached methods without arguments.
     * <p>
     * If the key name refers to a field, it can only be used with an @UpdateKey annotation specifying which is it.
     * <p>
     * Example:
     * <pre>
     * {@code
     * @UpdatesCache(name="getById", keys={"id"}, addIfAbsent=true)
     * public Transaction createPendingTransaction(@UpdateKey(name="id", fieldInTarget=true) Transaction pendingTransaction)
     *
     * }
     * </pre>
     */
    String[] keys() default "";

    String[] annotatedKeys() default "";

    String[] targetObjectKeys() default "";

    boolean removesValueFromSingleCollection() default false;//if the cache is on a collection, this removes a single object from the collection corresponding to a key

    boolean removesValueFromAllCollections() default false;//if the cache is on a collection, this removes a single object from the collection corresponding to a key

    boolean remove() default false; //if the cache is on a collection, this removes all objects from the collection corresponding to a key

    boolean invalidatesCache() default false;

    /**
     * If true, it adds the new value even if its' key did not already exist in the target cache.
     * If false, mnemosyne skips updating for absent keys.
     */

   // boolean addIfAbsent() default true;

    /**
     * Refers to one or more boolean values that have to be true before adding something new to the cache.
     * These values are either annotated as @UpdateKey with the given name, or are present as fields in the
     * target object. Booleans we want to be negative must start with an exclamation mark ('!').
     * <p>
     * * Example:
     * * <pre>
     *       {@code
     *       @UpdatesCache(name="getActiveUsers", keys={"email"}, addIfAbsent=true, conditionalAdd={"isActivated"})
     *       public Customer saveCustomerDetails(@Key(name="email") String email, @Key(name="isActivated") boolean isActivated)
     *
     *       }
     *       </pre>
     *
     * @return
     */
    String[] conditionalAdd() default "";

    String[] conditionalDelete() default "";


}
