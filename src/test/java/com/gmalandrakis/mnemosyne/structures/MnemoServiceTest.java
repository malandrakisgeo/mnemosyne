package com.gmalandrakis.mnemosyne.structures;

import com.gmalandrakis.mnemosyne.annotations.Cached;
import com.gmalandrakis.mnemosyne.core.MnemoService;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MnemoServiceTest {

    @Test
    public void verify_notCalledAgainIfCached() throws Throwable {
        var example = Mockito.spy(new TestObject());
        MnemoService mnemoService = new MnemoService();
        mnemoService.generateForClass(TestObject.class);
        var aaaa = mnemoService.getProxies().get(TestObject.class.getMethod("getStr", Integer.class));

        var p = aaaa.invoke(example, 1);
        assert (p != null);
        assert (p.equals("Yey!"));

        p = aaaa.invoke(example, 1);
        assert (p != null);
        assert (p.equals("Yey!"));
        verify(example, times(1)).getStr(any());
        p = aaaa.invoke(example, 2);
        assert (p != null);
        assert (p.equals("Yoy"));
        p = aaaa.invoke(example, 2);
        verify(example, times(2)).getStr(any());


    }




   public class TestObject {

        @Cached(countdownFromCreation = true)
        public String getStr(Integer i) {

            if(i == 1){
                return "Yey!";
            }
            return "Yoy";
        }
    }
}
