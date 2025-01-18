package com.gmalandrakis.mnemosyne.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface UpdateKey {


    /**
     * The name of the key, or the field of an object used as a key.
     * If it refers to a field in the target object, it is assumed it has the same name as here, unless the nameInTarget is set.
     */
    String name(); //TODO: Rename to KeyId

    /**
     * Specifies whether the key refers to the annotated value itself, or to a field of it.
     */
    boolean fieldInTarget() default false;

    /**
     * Named of the underlying field in the target object if it is different from the name.
     * Used only if fieldInTarget is true and ignored otherwise.
     * The name must be exactly the same as in the target object.
     */
    String nameInTarget() default "";

}
