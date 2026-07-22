package com.anisync.android.data.anisyncplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiringLruCacheTest {
    @Test
    fun `positive and negative values remain cached until explicit ttl expiry`() {
        var now = 1_000L
        val cache = ExpiringLruCache<String, List<Int>>(maxEntries = 4) { now }
        cache.put("positive", listOf(1), ttlMillis = 100)
        cache.put("negative", emptyList(), ttlMillis = 20)

        assertEquals(listOf(1), cache.get("positive"))
        assertEquals(emptyList<Int>(), cache.get("negative"))

        now += 21
        assertNull(cache.get("negative"))
        assertEquals(listOf(1), cache.get("positive"))

        now += 80
        assertNull(cache.get("positive"))
    }

    @Test
    fun `least recently used entry is evicted at the bound`() {
        val cache = ExpiringLruCache<String, Int>(maxEntries = 2) { 0L }
        cache.put("first", 1, ttlMillis = 100)
        cache.put("second", 2, ttlMillis = 100)
        assertEquals(1, cache.get("first"))

        cache.put("third", 3, ttlMillis = 100)

        assertNull(cache.get("second"))
        assertEquals(1, cache.get("first"))
        assertEquals(3, cache.get("third"))
        assertEquals(2, cache.size())
    }
}
