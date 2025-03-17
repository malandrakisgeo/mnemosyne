package com.gmalandrakis.mnemosyne.testobjects;

import com.gmalandrakis.mnemosyne.annotations.Cached;

public class classwithanno {

    @Cached(cacheName = "anno",countdownFromCreation = true)
    public String getStr(Integer i) {
        if(i == 1){
            return "Yey!";
        }
        return "Yoy";
    }
}
