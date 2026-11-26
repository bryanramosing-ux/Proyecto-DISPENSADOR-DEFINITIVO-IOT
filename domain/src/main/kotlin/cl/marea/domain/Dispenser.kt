package cl.marea.domain

import kotlinx.coroutines.flow.Flow

data class Reading(val level: Int, val cm: Int, val nonce: String, val active: Boolean, val present: Boolean)
sealed interface Signal {
    data class Status(val reading: Reading): Signal
    data class Reply(val id: String, val result: String, val cycles: Int): Signal
    data class Photo(val jpeg: ByteArray): Signal
    data class Link(val message: String): Signal
}
interface Device {
    val events: Flow<Signal>
    suspend fun connect()
    suspend fun startFeed(id: String, nonce: String)
    suspend fun stopFeed()
    fun close()
}
data class Entry(val id: String, val time: Long, val portions: Int, val result: String)
interface Journal {
    val entries: Flow<List<Entry>>
    suspend fun save(entry: Entry)
}
