### The project is under development and testing as of 3/2025.

# Mnemosyne
Mnemosyne is a small and customizable cache library for Java applications.

It enables multiple caches that return the same object types to use a common Value Pool where the returned
values are mapped to particular IDs, thereby allowing for more efficient memory management and 
easier updates.

It allows the developer to implement custom caching algorithms by simply implementing an abstract class. 
By default, mnemosyne includes implementations of FIFO, LRU, and S3-FIFO.

Mnemosyne currently works with Spring, but more integrations are coming.


## What problem does mnemosyne solve?

Let's say we work on a Java class with methods like this:

    Transaction getTransactionById(UUID transactionId);
    List<Transaction> getTransactionsBySeller(String username);
    List<Transaction> getTransactionsByBuyerAndSeller(String buyerId, String sellerId);
    List<Transaction> getTransactionsCompletedBetweenDates(Date from, Date to);
    List<Transaction> getPendingTransactions();
    List<Transaction> getTransactionByIds(Set<UUID> transactionIds); 

and other methods that add transactions or update them.

Suppose we need to run such methods extremely often, and we need fetch the transactions from a very slow remote database or REST-service,
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

But then we have other problems that arise.

What will the architecture of such a cache look like?<br>
Where will the job begin and how?<br>
How will we get all this work with just some annotations?<br>
How do we handle caches on methods that use collections of keys and return collections of values? Shall we assume a one-to-one correlation between the keys and the values,
and what should the cache do if it cannot be assumed?<br>
If collections of keys are used, and the ordering of the keys in the collection plays some role for the underlying method (e.g. collection of XY coordinates),
how can we ensure the cache does not mess up the results?<br>
If two key-collections differ by only one key, how can we ensure we won't waste memory by saving the same stuff twice?<br>
What is an ID and how would we handle types that lack particular IDs?<br>
What happens if the same ID exists in e.g. five distinct collections on three different caches, and want to remove it from just one without affecting the results of the others? <br>
What if we only want to update a cache on a condition, and not every time a method is called?<br>
How will we get this to work with at least a Java framework like Spring, let alone with Java projects in general?<br>
How will we test it, including the edge cases, and verify it works as expected?<br>
How should we synchronize reads and/or writes on a multithreaded and/or distributed environment?<br>
How do we make sure we have no "zombie" values, e.g. if we delete a user along with their transactions?<br>

And more.

Solving these problems is both logically and technically challenging.

Some of them are solved. Others are current TODOs.
Most led or lead to other challenges.

You are welcome to join our journey towards an even smarter cache!

## Using with Spring applications

If using Java 17 and Spring boot 3 or above, all you need to do is to annotate the main class with @Import(
MnemosyneSpringConf.class)
and the method you would like cached with @com.gmalandrakis.mnemosyne.annotations.Cached. 

The rest is done by the
library.

You can even use your own cache algorithms by extending the AbstractMnemosyneCache, and referring to them in the @Cache
annotation.

Feedback with results for other versions of Java or Spring, or even other JVM languages, will be appreciated.

## Using with non-Spring Java applications

(Coming soon)

# Use instructions & examples

### General
Once the library is configured for the project, the first thing you need to do is to define the IDs of the objects to be cached.
If the objects to be cached have an accessible  (i.e. either public or with a getter following Java naming conventions) field named Id (or ID, or id, or even iD), 
you don't need to do anything extra.
If it doesn't, or if you want to use another field as an ID, all you need to do is to annotate the related field(s) as @Id.
Multiple fields annotated with @Id form a compound Id.

You can then create a cache by just using an annotation on any Singleton object.

The caches can then be created with annotations above
the methods to be cached, as you see in the examples below:

    @Cached(cacheName = "transactionCache", capacity = 5000, timeToLive = 24 * 3600 * 1000, countdownFromCreation = true, cacheType = FIFOCache.class)
    public Transaction getTransactionById(String id);

    @Cached(cacheName = "customerCache2", capacity = 500)
    public Customer doSomethingAndReturnACustomer(@Key String id, String irrelevantForCaching, Boolean irrelevantBoolean) ;

    @Cached(cacheName = "getTransactionsByIds", capacity = 10000, allowSeparateHandlingForKeyCollections = true)
    List<Transaction> getTransactionByIds(Set<UUID> transactionIds);

    @Cached(cacheName = "getPendingTransactions")
    public List<Transaction> getPendingTransactions();

    @Cached(cacheName = "completedTransactionCache", capacity = 1000)
    public List<Transaction> getTransactionsByUser(String userId, boolean completed);
 
    @UpdatesCache(name="getTransactionById", targetObjectKeys={"id"}, addMode = AddMode.DEFAULT)
    @UpdatesCache(name="getTransactionByIds", targetObjectKeys={"id"}, addMode = AddMode.ADD_VALUES_TO_COLLECTION)
    @UpdatesCache(name="completedTransactionCache", targetObjectKeys={"userId", "isCompleted"}, addMode = AddMode.ADD_VALUES_TO_COLLECTION, conditionalAdd="isCompleted", 
    removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, conditionalRemove="!isCompleted")
    @UpdatesCache(name="getPendingTransactions", removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, conditionalRemove="!transaction.isCompleted")
    @UpdatesCache(name="getPendingTransactionsByUser", removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, addMode = AddMode.ADD_VALUES_TO_COLLECTION, 
    conditionalRemove="!transaction.isCompleted", conditionalAdd="transaction.isCompleted", targetObjectKeys="userId")
    public void saveTransaction(@UpdatedValue Transaction transaction);


Unless otherwise indicated by the presence of a @com.gmalandrakis.mnemosyne.annotations.Key annotation, all arguments are assembled to a 
CompoundKey used to retrieve the actual cache values. 

### Limitations & Precautions

#### Proxy objects
As of 3/2025, mnemosyne's default caching algorithms may not work properly with proxy objects.

Many frameworks and libraries for databases or REST- and SOAP-based services, wrap the returned values in proxy objects
that often lack a particular ID. There is a TODO on enabling support for enabling custom ID deduction, but
it currently is strongly recommended that object proxying is deactivated before mnemosyne is used.

Deactivating proxy objects differs from framework to framework, (e.g. in Hibernate it can be done by annotating the entities with @Proxy(lazy=false) ). 
Please check the documentation of the framework/library you use.

#### Collections as keys
As of 3/2025, methods that take a Collection as an argument will work properly only if they are an abstract Collection, Set, or List.
Using a concrete subclass, like e.g. ArrayList or HashSet, is explicitly forbidden in case you want to use special collection handling and will result to a RuntimeException.

When no special collection handling is enabled, though the use of e.g. ArrayLists is not forbidden, it may result to update discrepancies if another method updates the cached one via an @UpdatesCache annotation: the objects being updates via an @UpdatesCache annotation
are only wrapped in abstract Set or Lists, which are different from HashSets and ArrayLists, and will result to different keys.

In general, unless you have a 1-1 correlation between keys in a collection and returned values, or the collection is used as a whole (e.g. collection of XY coordinates), 
it is not recommended to use collections as keys in cached methods, especially when they are updated via mnemosyne. 

To understand why, consider the following use case: a method returning all the transactions by seller given a list of seller IDs. No 1-1 correlation can be assumed 
(a seller is most likely associated with more than one transaction). Calling the method with the list with the values "seller1" and "seller2" 
returns a different result than calling it with a "seller1", "seller3". If you cache this method, nothing particularly bad will happen: 
mnemosyne will just fetch the data for "seller1" twice. 
But if you proceed to updating the method via an @UpdatesCache annotation, as of 3/2025, you will get cache discrepancies.
It would be more prudent to cache an underlying method that takes each sellerId one by one and yields a result, especially if you want to update the cache.

    public List<Transaction> getTransactionsBySellersDoneRight(List<String> sellers){
        return sellers.stream().map(this::getTransactionsBySellerId).flatMap(List::stream).collect(Collectors.toList());
    }

    @Cached(cacheName = "doneRight") 
    public List<Transaction> getTransactionsBySeller(String sellerId) {
        return repository.getTransactionBySellerId(sellerId);
    }

    @Cached(cacheName = "doneWrong") //Will definitely result to cache discrepancies when updating
    public List<Transaction> getTransactionsBySellersDoneWrong(List<String> sellerIds) { //It would work perfectly with special handling enabled, but that requires a 1-1 correlation between sellerIds and Transactions.
        return repository.getTransactionsBySellerIds(sellerIds);
    }

Overcoming these limitations is a TODO.


## Future plans
Ideally, mnemosyne will become a full-fledged, high-performance, distributed cache easy to integrate with any kind of Java EE project.
But this is nothing one person can achieve alone, so feel free to contribute!

### Current major TODOs
* Test, test, test, test.
* Write more elaborate and cleaner documentation
* Improve the exception handling
* Overcome the limitations described earlier
* Add support for LRU and S3-FIFO
* Add better support for conditional update
* Add easy configuration for non-Spring applications
* Make all adjusts needed to make it work properly on distributed systems (final boss!)

### Mini TODOs
Well, dozens! From changing variable names to deciding when to update asynchronously.
You may find some in the issues too.

### TODOs under discusssion
* Add support for custom ID deduction (which solves the proxying problem)
* Add support for records

## Further documentation
As of 1/2025, the documentation is provided in the code itself as javadoc.
Running mvn javadoc:javadoc should suffice to generate a webpage with a general description.