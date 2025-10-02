package com.gmalandrakis.mnemosyne.annotations;

import com.gmalandrakis.mnemosyne.structures.AddMode;

public @interface Add {

    AddMode addMode() default AddMode.NONE;

    String[] addOnCondition()  default "";

    boolean conditionalANDGate() default true;
}
