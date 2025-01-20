package com.gmalandrakis.mnemosyne.structures;

import org.junit.Test;

public class CompoundKeyAndIdTest {

    @Test
    public void testNullHandling() {
        var str1 = new String("testString");

        var compoundKeyObjects1 = new Object[]{str1, null, null, "testString2", null};
        var compoundKeyObjects2 = new Object[]{"testString", null, null, "testString2", null};
        var compoundKey1 = new CompoundKey(compoundKeyObjects1);
        var compoundKey2 = new CompoundKey(compoundKeyObjects2);
        assert (compoundKey1.equals(compoundKey2));

        compoundKeyObjects2 = new Object[]{null, str1, null, "testString2", null};
        compoundKey2 = new CompoundKey(compoundKeyObjects2);
        assert (!compoundKey1.equals(compoundKey2));

       var compoundKeyObjects3 = new Object[]{str1, "testString2"};
        var compoundKey3 = new CompoundKey(compoundKeyObjects3);
        assert (!compoundKey1.equals(compoundKey3));
        assert (!compoundKey2.equals(compoundKey3));

    }

    @Test
    public void testNumberHandling() {
        var compoundKeyObjects1 = new Object[]{1, 1L};
        var compoundKeyObjects2 = new Object[]{Integer.valueOf(1), Long.valueOf(1)};
        var compoundKey1 = new CompoundKey(compoundKeyObjects1);
        var compoundKey2 = new CompoundKey(compoundKeyObjects2);
        assert (compoundKey1.equals(compoundKey2));


    }
}
