# (Under construction)

## API
(coming soon)

## Implementing a custom cache

(coming soon)

## Using special-handling
(coming soon)

## Precautions

### Proxy objects
As of 10/2025, mnemosyne's default caching algorithms may not work properly with proxy objects.

Many frameworks and libraries for databases or REST- and SOAP-based services, wrap the returned values in proxy objects
that often lack a particular ID. There is a TODO on enabling support for enabling custom ID deduction, but
it currently is strongly recommended that object proxying is deactivated before mnemosyne is used.

Deactivating proxy objects differs from framework to framework, (e.g. in Hibernate it can be done by annotating the entities with @Proxy(lazy=false) ).
Please check the documentation of the framework/library you use.

### Collections as keys
As of 10/2025, methods that take a Collection as an argument will work properly only if they are an abstract Collection, Set, or List.
Using a concrete subclass, like e.g. ArrayList or HashSet, is explicitly forbidden in case you want to use special collection handling and will result to a RuntimeException.

When no special collection handling is enabled, though the use of e.g. ArrayLists is not forbidden, it may result to update discrepancies if another method updates the cached one via an @UpdatesCache annotation: the objects being updates via an @UpdatesCache annotation
are only wrapped in abstract Set or Lists, which are different from HashSets and ArrayLists, and will result to different keys.

In general, unless you have a 1-1 correlation between keys in a collection and returned values, or the collection is used as a whole (e.g. collection of XY coordinates),
it is not recommended to use collections as keys in cached methods, especially when they are updated via mnemosyne.

To understand why, consider the following use case: a method returning all the transactions by seller given a list of seller IDs. No 1-1 correlation can be assumed
(a seller is most likely associated with more than one transaction). Calling the method with the list with the values "seller1" and "seller2"
returns a different result than calling it with a "seller1", "seller3". If you cache this method, nothing particularly bad will happen:
mnemosyne will just fetch the data for "seller1" twice.
But if you proceed to updating the method via an @UpdatesCache annotation, as of 10/2025, you will get cache discrepancies.
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


## Legacy API
Before the @UpdatesValuePool was introduced, the user could only update caches using the @UpdatesCache annotation.

The user annotated one or more methods with @Cached, and could update the contents by annotating one or more
Methods with an @UpdatesCache, where the mode of operation was specified along with addition/removal conditions.

Even though it was practical for up to several caches, needing two annotations every time is not developer friendly.
The result would look like this (still a technically valid use of mnemosyne): 

    @Cached(cacheName = "transactionCache", capacity = 5000, timeToLive = 24 * 3600 * 1000, countdownFromCreation = true, cacheType = FIFOCache.class)
    public Transaction getTransactionById(String id);

    @Cached(cacheName = "getTransactionsByIds", capacity = 10000, allowSeparateHandlingForKeyCollections = true)
    List<Transaction> getTransactionByIds(Set<UUID> transactionIds);

    @Cached(cacheName = "getPendingTransactions")
    public List<Transaction> getPendingTransactions();

    @Cached(cacheName = "completedTransactionCache", capacity = 1000)
    public List<Transaction> getTransactionsByUser(String userId, boolean completed);
 
    @UpdatesCache(name="getTransactionById", targetObjectKeys={"id"}, addMode = AddMode.DEFAULT)
    @UpdatesCache(name="getTransactionByIds", targetObjectKeys={"id"}, addMode = AddMode.ADD_VALUES_TO_COLLECTION)
    @UpdatesCache(name="completedTransactionCache", targetObjectKeys={"userId", "isCompleted"}, addMode = AddMode.ADD_VALUES_TO_COLLECTION, addOnCondition="isCompleted", 
    removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, removeOnCondition="!isCompleted")
    @UpdatesCache(name="getPendingTransactions", removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, removeOnCondition="!transaction.isCompleted")
    @UpdatesCache(name="getPendingTransactionsByUser", removeMode = RemoveMode.REMOVE_VALUE_FROM_COLLECTION, addMode = AddMode.ADD_VALUES_TO_COLLECTION, 
    removeOnCondition="!transaction.isCompleted", addOnCondition="transaction.isCompleted", targetObjectKeys="userId")
    public void saveTransaction(@UpdatedValue Transaction transaction);

Mnemosyne would then update the cache with the key, id, and value. Updating the value pool was a responsibility
of the cache itself: every addition or removal to/from the ValuePool of a type, took place via the caches
that returned objects of that type.

In retrospect, it seems obvious that one could just annotate a method via an @UpdatesValuePool annotation, but it
was not equally apparent in the beginning -when mnenosyne did not even have a name and was only used to cache a couple of Methods 
in a small project, where only one needed to be manually updated.

An extensive restructuring of Mnemosyne was needed before @UpdatesValuePool could work: instead of relying on the caches
to update the value pool with the latest versions of objects, Mnemosyne itself would now take care of it, and the caches
would only inform the ValuePool that they are using a particular ID.

The new API transferred the responsibility of update mode and conditions to the @Cached annotation. 
These are still present in the plain old @UpdatesCache, but may be phased out in the future.

