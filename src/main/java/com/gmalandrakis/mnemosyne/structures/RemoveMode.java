package com.gmalandrakis.mnemosyne.structures;

public enum RemoveMode {

    /**
     * No removals
     */
    NONE,
    /**
     * Removes a key from a cache, and updates the number of uses of the associated IDs.
     * In the default mnemosyne cache algorithms, this means that the keys are deleted, and the number of uses for associated IDs
     * is decreased. If it is zero, the ValuePool is informed that a cache no longer uses one or more IDs at all.
     * It is then up to the valuePool to decide whether the IDs and respective objects are kept or removed from it too.
     */
    SINGLE_VALUE,
    /**
     * Removes a particular ID from one collection only. A key is necessary for this to happen.
     * May only be used with caches that return a collection, otherwise ignored.
     */
    REMOVE_FROM_COLLECTION,
    /**
     * Removes a particular ID from all available collections.
     * No key is necessary for this operation, and the ID is enough.
     * May only be used with caches that return a collection, otherwise ignored.
     */
    REMOVE_FROM_ALL_COLLECTIONS,

    /**
     * Invalidates cache completely.
     */
    INVALIDATE_CACHE
}
