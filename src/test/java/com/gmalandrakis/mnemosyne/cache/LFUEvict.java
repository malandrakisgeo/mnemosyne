package com.gmalandrakis.mnemosyne.cache;

import com.gmalandrakis.mnemosyne.cache.old.LFUCache;
import com.gmalandrakis.mnemosyne.structures.CacheParameters;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

public class LFUEvict {
  /*  private static final int upperLimit = 1000; //I always looked for an opportunity to use this term in other contexts than math problems
    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    private LFUCache<Long, String> lfu;

    CacheParameters cacheParameters;
    @Before
    public void prepare() {
        CacheParameters cacheParameters = new CacheParameters();
        cacheParameters.setCapacity(upperLimit / 2);
        cacheParameters.setCacheName("test");
        cacheParameters.setThreadPoolSize(1);
        this.cacheParameters = cacheParameters;
        lfu = Mockito.spy(new LFUCache<>(cacheParameters));
    }

    @Test
    public void testLFU() throws InterruptedException {

        //LFUCache<Long, String> lfu = new LFUCache<>(cacheParameters);

        fillWithData(lfu, 0);
        assert (lfu.cachedValues.size() <= upperLimit / 2);
        verify(lfu, atLeast(1)).evict();
        accessEven(lfu);
        lfu.setEvictNext();
        var val = lfu.cachedValues.get(lfu.evictNext.get(0)); //We don't want to increase the hits
        assert (val!=null);
        assert (val.getHits()<lfu.cachedValues.get(lfu.evictNext.get(0)+1).getHits());
        fillWithData(lfu, 0);



    }

    @Test
    public void testAsync_noExceptions() {
        lfu = Mockito.spy(new LFUCache<>(cacheParameters));
        executorService.execute(() -> this.fillWithData(lfu, upperLimit)); //assert no exceptions
        executorService.execute(lfu::evict); //assert no exceptions
        executorService.execute(lfu::invalidateCache); //assert no exceptions
        executorService.execute(() -> this.fillWithData(lfu, upperLimit)); //assert no exceptions
        executorService.execute(lfu::invalidateCache); //assert no exceptions
        executorService.shutdown();
        lfu.invalidateCache();


        assert(lfu.cachedValues.isEmpty());
    }

    @Test
    public void asynctest() {
        executorService.execute(lfu::setEvictNext); //assert no exceptions
        executorService.execute(lfu::setEvictNext); //assert no exceptions
        executorService.execute(lfu::setEvictNext); //assert no exceptions
        if(executorService.submit(lfu::setEvictNext).isDone()); //assert no exceptions
        {
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {

        }
    }


    private void fillWithData(LFUCache cache, long begin) {
        for (long i = begin; i < upperLimit + begin; i++) {
            cache.put(i, String.valueOf(i));
        }
    }


    private void accessEven(LFUCache cache){
        var keySet = cache.cachedValues.keySet();
        int i = 0;
        for (Object o : keySet) {
            ++i;
            if(i % 2 == 0){
               var ooo = cache.get(o);
               if(ooo == null){
                   System.out.println("wow");
                   throw new RuntimeException();
               }
               ooo.hashCode();
            }
        }

    }*/
}
