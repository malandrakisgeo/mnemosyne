## Using with Spring applications

If using Java 17 and Spring boot 3 or above, all you need to do is to annotate the main class with @Import(
MnemosyneSpringConf.class) .
Feedback with results for other versions of Java or Spring will be appreciated.

## Using with non-Spring Java applications

(Coming soon)


## Current TODOs 



## Future plans
Make it suitable for distributed caches. 
The original design was based on the assumption that the application instance and the cache are running on the same machine.
Making a distributed Cache out of Mnemosyne should not be impossible, but may require major structural modifications. 