package com.gmalandrakis.mnemosyne.structures;

/**
 * The structure used to wrap IDs internally in implementations of {@link com.gmalandrakis.mnemosyne.cache.AbstractGenericCache AbstractGenericCache},
 * along with metadata such as creation time, last access time, and number of hits.
 * <p>
 * A key can correspond to either an ID (in caches that return single values for a key, ), or in multiple IDs (in caches
 * that return whole Collections for every ID). Hence the two implementations of this class.
 *
 * @author George Malandrakis (malandrakisgeo@gmail.com)
 */
public abstract class IdWrapper<ID> {

    /**
     * Timestamp of last access.
     * <p>
     * By default, this will be increased only on explicit gets, not on updates. The logic behind
     * this choice is that updates can come from automated procedures and are not synonymous to explicit accesses by users.
     * <p>
     * You may use explicit calls to updateLastAccessed() in custom implementations of AbstractMnemosyneCache for updating the timestamp even when updating the value.
     */
    long lastAccessed;

    /**
     * Timestamp of creation time.
     */
    long createdOn;

    /**
     * Number of cache hits.
     * <p>
     * By default, this will be increased only on explicit gets, not on updates. The logic behind
     * this choice is that updates can come from automated procedures and are not synonymous to explicit accesses by users.
     * <p>
     * You may use explicit calls to increaseHits() in custom implementations of AbstractMnemosyneCache for updating the timestamp even when updating the value.
     */
    int hits;

    public void updateLastAccessed() {
        this.lastAccessed = System.currentTimeMillis();
    }

    public long getLastAccessed() {
        return lastAccessed;
    }

    public void increaseHits() {
        hits += 1;
    }

    public int getHits() {
        return hits;
    }

    public long getCreatedOn() {
        return createdOn;
    }
}
