package com.gmalandrakis.mnemosyne.structures;

import com.gmalandrakis.mnemosyne.annotations.Cached;

public class TestObject { //TODO: Use an inner class somewhere.

    @Cached(countdownFromCreation = true)
    public String getStr(Integer i) {

        if(i == 1){
            return "Yey!";
        }
        return "Yoy";
    }
}
