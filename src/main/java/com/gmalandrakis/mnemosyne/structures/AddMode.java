package com.gmalandrakis.mnemosyne.structures;

public enum AddMode {

    /**
     * No additions.
     */
    NONE,
    /**
     * If a value is updated, the previous key now references the newest object.
     * <p>
     * If a (non special-handling) collection cache contains no values for the particular key (e.g. the underlying method has never been called before),
     * the underlying method is called preemptively to avoid cache discrepancies.
     */
    SINGLE_VALUE,
    /**
     * For collection caches, both normal and special-handling.
     * The new or updated values are added to the collection. A proper remove mode is needed to remove the outdated ones, if that is necessary.
     * <p>
     * A key is necessary for the operation, except for functions that take no arguments.
     * <p>
     * If a non special-handling cache contains no values for the particular key (e.g. the underlying method has never been called before),
     * the underlying method is called preemptively to avoid cache discrepancies.
     * <p>
     */
    ADD_TO_COLLECTION,
    /**
     * For non special-handling collection caches. Adds a particular ID to all available collections in the cache.
     * <p>
     * The new or updated values are added to all cached collections and the value pool, and the current values remain unchanged.
     * A proper remove mode is needed to remove the outdated ones, if that is necessary.
     * <p>
     * No key is necessary for the operation, as the values are added to all collections.
     * <p>
     * The current values are assumed to be up-to-date and complete, so no preemptive adding is done.
     * <p>
     * <b>Using with special-handling caches may lead to problems.</b>
     */
    ADD_TO_ALL_COLLECTIONS,
    /**
     * For collection caches.
     * A key is necessary, and any existing collection for this key is removed to be replaced by another collection.
     * Logically equivalent a DEFAULT removal mode combined with ADD_VALUES_TO_COLLECTION
     */
    REPLACE_EXISTING_COLLECTION //key necessary
}
