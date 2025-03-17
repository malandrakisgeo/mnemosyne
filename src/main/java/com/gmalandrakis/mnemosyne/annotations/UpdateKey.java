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
    String keyId();


}
