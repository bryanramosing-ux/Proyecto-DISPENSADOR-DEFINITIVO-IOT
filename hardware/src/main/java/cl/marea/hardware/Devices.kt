package cl.marea.hardware

import cl.marea.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

class BrokerDevice(host: String, private val device: String, private val user: String, private val pass: String): Device, MqttCallback {
    init {
        require(host.matches(Regex("[a-zA-Z0-9.-]+")) && device.matches(Regex("[a-zA-Z0-9_-]{1,48}"))) { "Revisa el host y el ID del equipo" }
    }
    private val client = MqttAsyncClient("ssl://$host:8883", "mov-" + UUID.randomUUID().toString().take(16), MemoryPersistence())
    private val root = "dispensador/$device"
    private val queue = Channel<Signal>(64)
    override val events = queue.receiveAsFlow()
    override suspend fun connect() = withContext(Dispatchers.IO) {
        client.setCallback(this@BrokerDevice)
        val options = MqttConnectOptions().apply {
            userName = user
            password = pass.toCharArray()
            connectionTimeout = 8
            keepAliveInterval = 20
            isCleanSession = true
            setHttpsHostnameVerificationEnabled(true)
        }
        client.connect(options).waitForCompletion(12000)
        client.subscribe(arrayOf("$root/state","$root/ack","$root/jpeg"), intArrayOf(0,1,0)).waitForCompletion(8000)
    }
    override fun connectionLost(cause: Throwable?) { queue.trySend(Signal.Link("Broker desconectado. Vuelve a conectar.")) }
    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    override fun messageArrived(topic: String, message: MqttMessage) {
        if (message.isRetained) return
        if (topic == "$root/jpeg") {
            if (message.payload.size in 100..60000) queue.trySend(Signal.Photo(message.payload))
            return
        }
        runCatching {
            val j = JSONObject(String(message.payload, Charsets.UTF_8))
            when (topic) {
                "$root/state" -> queue.trySend(Signal.Status(Reading(j.getInt("level"),j.getInt("cm"),j.getString("nonce"))))
                "$root/ack" -> queue.trySend(Signal.Reply(j.getString("id"), if (j.getBoolean("ok")) "Ciclo completado" else "Rechazado"))
                else -> Unit
            }
        }
    }
    private suspend fun publish(path: String, body: String) = withContext(Dispatchers.IO) {
        client.publish("$root/$path", body.toByteArray(), 0, false).waitForCompletion(5000)
    }
    override suspend fun feed(id: String, portions: Int, nonce: String) {
        publish("cmd", JSONObject().put("id",id).put("portions",portions).put("nonce",nonce).toString())
    }
    override suspend fun capture(live: Boolean) { publish("photo", if(live) "live" else "capture") }
    override fun close() {
        queue.close()
        Thread {
            runCatching { client.disconnectForcibly(0, 300) }
            runCatching { client.close(true) }
        }.start()
    }
}

class Simulator: Device {
    private val queue = Channel<Signal>(64)
    override val events = queue.receiveAsFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var level = 78
    private var last = 0L
    private var used = 0
    private var day = java.time.LocalDate.now()
    private var key = UUID.randomUUID().toString()
    @Synchronized private fun emit() { queue.trySend(Signal.Status(Reading(level,24,key))) }
    override suspend fun connect() { scope.launch { while (isActive) { emit(); delay(2000) } } }
    @Synchronized private fun simulate(id: String, portions: Int, nonce: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (day != java.time.LocalDate.now()) { day = java.time.LocalDate.now(); used = 0 }
        val ok = key == nonce && portions in 1..3 && level >= portions*4 && (last == 0L || now-last >= 10000) && used+portions <= 12
        if (ok) { used += portions; level -= portions*4; last = now; key = UUID.randomUUID().toString() }
        queue.trySend(Signal.Reply(id, if (ok) "Ciclo completado" else "Rechazado"))
        emit()
    }
    override suspend fun feed(id: String, portions: Int, nonce: String) = simulate(id,portions,nonce)
    override suspend fun capture(live: Boolean) { queue.trySend(Signal.Link("Simulación · no hay una cámara física")) }
    override fun close() { scope.cancel(); queue.close() }
}
