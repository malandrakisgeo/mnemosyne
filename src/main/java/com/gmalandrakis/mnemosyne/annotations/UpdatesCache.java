package com.gmalandrakis.mnemosyne.annotations;

import com.gmalandrakis.mnemosyne.structures.AddMode;
import com.gmalandrakis.mnemosyne.structures.RemoveMode;

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
     * @UpdatesCache(name="getTransactionsByUser", annotatedKeys={"userId", "pending"}) //Could lead to a class-cast exception if it was {"pending", "userId"}, and would lead to silent cache discrepancies if both were Strings in the method and the ordering was wrong!
     * public Transaction createTransactionForUser(@UpdateKey(name="userId") String userId, @UpdateKey(name="pending") boolean pending, @UpdateKey UUID uuid)
     *
     * }
     * </pre>
     */
    String[] annotatedKeys() default "";

    /**
     * The ordering of arguments in a method under update.
     * Used only if a combination of annotatedKeys and targetObjectKeys is in use, otherwise ignored.
     */
    String[] keyOrder() default "";

    /**
     See {@link Cached#targetObjectKeys targetObjectKeys }.
     Used even if no @UpdatesValuePool is present.
     */
    String[] targetObjectKeys() default "";

    /**
     See {@link Cached#addMode()}  addMode }
     */
    AddMode addMode() default AddMode.NONE;

    /**
     See {@link Cached#removeMode()}  removeMode }
     */
    RemoveMode removeMode() default RemoveMode.NONE;

    /**
     See {@link Cached#addOnCondition()}  addOnCondition }
     */
    String[] addOnCondition() default "";

    /**
     See {@link Cached#removeOnCondition()}  removeOnCondition }
     */
    String[] removeOnCondition() default "";

    /**
     See {@link Cached#complementaryCondition()}  complementaryCondition }
     */
    boolean complementaryCondition() default false;

    /**
     See {@link Cached#conditionalANDGate()}  conditionalANDGate }
     */
    boolean conditionalANDGate() default true;


}
//It seems possible to copy or move some of these functionalities to @Cached. This would reduce the number of annotations necessary.
//Except for Cache Invalidation and edge cases where annotatedKeys are involved, the rest ought to be moved. Add/remove modes and conditions and target object keys for instance.
//TODO: Check how feasible it is, and act accordingly