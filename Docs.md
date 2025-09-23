# (Under construction)

## API
(coming soon)

## Implementing a custom cache

(coming soon)

## Using special-handling
(coming soon)

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

