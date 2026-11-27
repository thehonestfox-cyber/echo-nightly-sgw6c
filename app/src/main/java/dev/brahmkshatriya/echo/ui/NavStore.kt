package dev.brahmkshatriya.echo.ui

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tiny in-memory store used to hand complex (non-serializable or large) objects such as
 * [dev.brahmkshatriya.echo.common.models.Feed] between navigation destinations. Routes only
 * carry the key. Payloads are kept until the process dies; screens must tolerate a missing one.
 */
object NavStore {
    private val counter = AtomicLong()
    private val map = ConcurrentHashMap<String, Any>()

    fun put(value: Any): String {
        val key = counter.incrementAndGet().toString()
        map[key] = value
        if (map.size > 64) map.keys.sortedBy { it.toLong() }.take(map.size - 64).forEach(map::remove)
        return key
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String?): T? = key?.let { map[it] as? T }
}
