# Mnemosyne
Mnemosyne is a cache library for Java applications.

## Why mnemosyne?
Many if not most of the existing cache libraries are unnecessarily complex, making it difficult even for experienced programmers
to understand how they work, let alone customize them for their needs.

Mnemosyne, on the contrary, is small yet robust, easy to grasp, and easy to customize. No bloated code, no package chaos, no 
countless external dependencies.

## Using with Spring applications

If using Java 17 and Spring boot 3 or above, all you need to do is to annotate the main class with @Import(MnemosyneSpringConf.class) .
Feedback with results for other versions of Java or Spring, or even other JVM languages, will be appreciated.

## Using with non-Spring Java applications

(Coming soon)

## Current TODOs 
Add built-in support for distributed caches.
Add easy configuration for non-Spring applications.

## Future plans
The original design was based on the assumption that the application instance and the cache are running on the same machine.
Making a distributed Cache out of Mnemosyne should not be impossible, but may require major structural modifications. 