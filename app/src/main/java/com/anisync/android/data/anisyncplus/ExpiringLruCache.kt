package com.anisync.android.data.anisyncplus

/** Small deterministic LRU used by the optional AniList recovery pools. */
internal class ExpiringLruCache<K, V>(
    private val maxEntries: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(val value: V, val expiresAtMillis: Long)

    private val entries = LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true)

    init {
        require(maxEntries > 0)
    }

    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= nowMillis()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V, ttlMillis: Long) {
        require(ttlMillis > 0)
        entries[key] = Entry(value, nowMillis() + ttlMillis)
        while (entries.size > maxEntries) entries.remove(entries.entries.first().key)
    }

    fun size(): Int = entries.size
}
