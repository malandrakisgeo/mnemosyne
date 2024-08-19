### The project is under construction and testing, and not yet ready for production as of 8/2028.

# Mnemosyne
Mnemosyne is a small and customizable cache library for Java applications.

## Why mnemosyne?
Many if not most of the existing cache libraries are unnecessarily complex, making it difficult even for experienced programmers
to understand how they work, let alone customize them for their needs.

Mnemosyne, on the contrary, is small yet robust, easy to grasp, and easy to customize. No bloated code, no package chaos, no 
countless external dependencies.

## Using with Spring applications

If using Java 17 and Spring boot 3 or above, all you need to do is to annotate the main class with @Import(
MnemosyneSpringConf.class)
and the method you would like cached with @com.gmalandrakis.mnemosyne.annotations.Cached. The rest is done by the
library.

You can even use your own cache algorithms by extending the AbstractMnemosyneCache, and referring to them in the @Cache
annotation.

Feedback with results for other versions of Java or Spring, or even other JVM languages, will be appreciated.

## Using with non-Spring Java applications

(Coming soon)

# Examples
Once the library is configured for the project, the caches can be created with annotations above
the methods to be cached.

    @Cached(cacheName = "customerCache", capacity = 500, timeToLive = 24 * 3600 * 1000, countdownFromCreation = true, cacheType = FIFOCache.class)
    public Customer getCustomerById(String id) {
        ...
    }

    @Cached(cacheName = "transactionCache", capacity = 1000, timeToLive = 12 * 3600 * 1000, countdownFromCreation = false, preemptiveEvictionPercentage = 85, evictionStepPercentage = 5,  cacheType = LFUCache.class)
    public List<Transaction> getTransactionsByUser(String id, boolean onlySuccessful) {
        ...
    }

    @Cached(cacheName = "customerCache2", capacity = 500)
    public Customer getCustomerByIdWithExtraSteps(@Key String id, String irrelevantForCaching) {
        ...
    }

Unless otherwise indicated by the presence of a @com.gmalandrakis.mnemosyne.annotations.Key annotation, all arguments are assembled to a CompoundKey used to retrieve the actual cache values.



## Current TODOs 
* Add easy configuration for non-Spring applications.
* Add built-in support for distributed caches.

## Future plans
The original design was based on the assumption that the application instance and the cache are running on the same machine.
Making a distributed Cache out of Mnemosyne should not be impossible, but may require major structural modifications. 

## Further documentation
As of 7/2024, the documentation is provided in the code itself as javadoc.
Running mvn javadoc:javadoc should suffice to generate a webpage with a general description.