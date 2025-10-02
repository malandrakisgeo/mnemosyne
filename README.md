### The project is under development and testing as of 10/2025.

# Mnemosyne
Mnemosyne is a small and customizable cache library for Java applications.
It uses an in-memory database of values and their respective IDs for every cached type.
This allows the simultaneous update of multiple caches at once, thereby allowing for more efficient memory management and 
easier updates.

Mnemosyne allows the developer to implement domain-specific caching algorithms by simply extending an abstract class. 
By default, mnemosyne includes implementations of FIFO and LRU.

Mnemosyne currently works with Spring, but more integrations are coming.

## Basic idea
The basic idea is that, when multiple Java Methods returning the same object type are cached, it should be possible to update all
their caches at once. One can assign a unique ID to a cached object, and that ID can then be used for simultaneous updates of multiple caches.

The objects may be returned from a cache as they are, or as collection elements. Some caches may be subject to conditional updates.
Mnemosyne is designed with these considerations in mind.

The basic structure of mnemosyne is easy to understand with a practical example: an application that caches transactions.

![Mnemosyne structure](structure-by-example.png?raw=true)

When an application calls a Method cached by mnemosyne, the arguments are assembled to a CompoundKey. The CompoundKey is then used
to retrieve from a local mnemosyne cache the IDs of the objects to be returned. These objects are stored in a common
Value Pool for the object type, mapped to their IDs.

When an application calls a Method that updates an object of a cached type, the operation is propagated to the Value Pool,
effectively updating all caches at once with the latest version of an object. Local caches may have values
added or removed on conditions.

If an object with a particular ID is updated, it is updated in the Value Pool and hence updated for all caches at once.


## What problem does mnemosyne solve?

Suppose we work with multiple Methods that return instances or collections of the same type (e.g. Transaction):

    Transaction getTransactionById(UUID transactionId);
    List<Transaction> getTransactionsBySeller(String username);
    List<Transaction> getTransactionsByBuyerAndSeller(String buyerId, String sellerId);
    List<Transaction> getTransactionsCompletedBetweenDates(Date from, Date to);
    List<Transaction> getPendingTransactions();
    List<Transaction> getTransactionByIds(Set<UUID> transactionIds); 

and other methods that add objects or update them.

Suppose we need to run such methods extremely often, and we need fetch the objects from a very slow remote database or REST-service,
so we need to rely on a cache as much as possible.

What happens if a transaction is updated, e.g. completed or cancelled?

If all the aforementioned methods are cached, we could have an old version of the transaction in
all four Lists: one for getTransactionsByUser, one for getTransactionsByBuyerAndSeller, one for getTransactionsCompletedBetweenDates,
and one in getPendingTransactions.
We want a cache that automatically updates the value in the three first lists, and removes it from the fourth, and does it fast.

In this project, we do our very best to solve the problem by creating what is essentially an in-memory database of value pools for every cached type,
mapped by IDs.

The caches for each method do not store the values themselves, but their associated IDs.

Whenever a method is invoked with some arguments, those are used as a key that is mapped to IDs instead of objects.
The objects are then retrieved and returned from the ValuePool via their IDs.

Whenever an object is to be updated, we just remove the old version and put the newer one for the same ID, thereby simplifying the
update of multiple caches at the same time.

In the previous example, we would have a ValuePool with all cached transactions mapped by their unique IDs.
Whenever a transaction is updated, the cache only needs to invalidate the old version and replace it with the newer one:
we will only update one place instead of three.

The caches of the methods are only linked to IDs instead of transaction objects, so we won't need to update something there as long as
an object is not deleted.

## Using with Spring applications

If using Java 17 and Spring boot 3 or above, all you need to do is to annotate the main class with @Import(MnemosyneSpringConf.class)
and the method you would like cached with @com.gmalandrakis.mnemosyne.annotations.Cached. 

The rest is done by the  library.

You can even use your own cache algorithms by extending the AbstractMnemosyneCache, and referring to them in the @Cache
annotation.

Feedback with results for other versions of Java or Spring, or even other JVM languages, will be appreciated.

## Using with non-Spring Java applications

(Coming soon)

# Use instructions & examples

### General use
Once the library is configured for the project, the first thing you need to do is to define the IDs of the objects to be cached.

If the objects to be cached have an accessible (i.e. either public or with a getter following Java naming conventions) field named Id (or ID, or id, or even iD), 
you don't need to do anything extra.
If it doesn't, or if you want to use another field as an ID, all you need to do is to annotate the related field(s) as @Id.
Multiple fields annotated with @Id form a compound Id.

You can then create a cache by just using an annotation on any Singleton object.

The caches can then be created with annotations above
the methods to be cached, as you see in the examples below:

    @UpdatesValuePool(remove = true)
    public void deleteTransaction(Transaction transaction);

    @Cached(cacheName = "transactionCache", , targetObjectKeys={"id"}, addMode = AddMode.DEFAULT,
    capacity = 5000, timeToLive = 24 * 3600 * 1000, countdownFromCreation = true, cacheType = FIFOCache.class)
    public Transaction getTransactionById(String id);

    @Cached(cacheName = "getTransactionsByIds", targetObjectKeys={"id"}, addMode = AddMode.ADD_VALUES_TO_COLLECTION,
    capacity = 10000, allowSeparateHandlingForKeyCollections = true)
    List<Transaction> getTransactionByIds(Set<UUID> transactionIds);

    @Cached(cacheName = "getPendingTransactions", addMode = RemoveMode.ADD_VALUES_TO_COLLECTION, addOnCondition="!isCompleted"
    removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, removeOnCondition="isCompleted")
    public List<Transaction> getPendingTransactions();

    @Cached(cacheName = "completedTransactionByUserCache", capacity = 1000, removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, 
    addMode = AddMode.ADD_VALUES_TO_COLLECTION, removeOnCondition="!isCompleted", addOnCondition="isCompleted", targetObjectKeys="userId")
    public List<Transaction> getCompletedTransactionsByUser(String userId, boolean completed);

    @UpdatesValuePool(addIfAbsent = true)
    public void saveTransaction(@UpdatedValue Transaction transaction);

    @UpdatesValuePool(remove = true)
    public void deleteTransaction(Transaction transaction);

    @UpdatesCache(name="completedTransactionByUserCache", targetObjectKeys={"id"}, addMode = AddMode.DEFAULT)
    public void markTransactionAsCompleted(@Key String userId, @Key UUID transactionId);


Unless otherwise indicated by the presence of a @com.gmalandrakis.mnemosyne.annotations.Key annotation, all arguments are assembled to a 
CompoundKey used to retrieve the actual cache values. 

### Implementing custom caching algorithms

As of 10/2025 a generic implementation of a FIFO and an LRU are provided by mnemosyne. An S3-FIFO and an LFU are under construction.
But since many projects have domain-specific needs and eviction policies, users are able to implement their own caching algorithms
by extending the AbstractMnemosyneCache class and implementing its' abstract methods.

AbstractMnemosyneCache provides a specification of what mnemosyne expects from a caching algorithm in order to function.
Any cache algorithm following this specification should be able to work with mnemosyne without problems.


## Future plans
Ideally, mnemosyne will become a full-fledged, high-performance, distributed cache easy to integrate with any kind of Java EE project.
But this is nothing one person can achieve alone, so feel free to contribute!

### Current major TODOs
* Test, test, test, test.
* Write more elaborate and cleaner documentation
* Improve the exception handling
* Overcome the limitations described earlier
* Add support for LFU and S3-FIFO
* Add better support for conditional update
* Add easy configuration for non-Spring applications
* Make all adjustments needed to make it work properly on distributed systems (final boss!)

### Mini TODOs
Well, dozens! From changing variable names to deciding when to update asynchronously.
You may find some in the issues too.

### TODOs under discusssion
* Add support for custom ID deduction (which solves the proxy problem)
* Add support for records

## Further documentation
As of 10/2025, most of the documentation is provided in the code itself as javadoc.
Running mvn javadoc:javadoc should suffice to generate a webpage with a general description.
You may also check the Docs.md (which is under construction)