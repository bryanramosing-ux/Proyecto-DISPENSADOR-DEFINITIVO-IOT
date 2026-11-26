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
                "$root/state" -> queue.trySend(Signal.Status(Reading(j.getInt("level"),j.getInt("cm"),j.getString("nonce"),j.getBoolean("active"),j.getBoolean("present"))))
                "$root/ack" -> queue.trySend(Signal.Reply(j.getString("id"), if (j.getBoolean("ok")) "Detenido" else "Rechazado", j.getInt("cycles")))
                else -> Unit
            }
        }
    }
    private suspend fun publish(path: String, body: String) = withContext(Dispatchers.IO) {
        client.publish("$root/$path", body.toByteArray(), 0, false).waitForCompletion(5000)
    }
    override suspend fun startFeed(id: String, nonce: String) {
        publish("cmd", JSONObject().put("id",id).put("action","start").put("nonce",nonce).toString())
    }
    override suspend fun stopFeed() { publish("cmd", JSONObject().put("action","stop").toString()) }
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
    private var activeId: String? = null
    private var cycles = 0
    private var due = 0L
    private fun emit() { queue.trySend(Signal.Status(Reading(level,24,key,activeId!=null,true))) }
    override suspend fun connect() { scope.launch { while (isActive) { tick(); delay(350) } } }
    private fun finish() {
        activeId?.let { queue.trySend(Signal.Reply(it,"Detenido",cycles)) }
        activeId=null
        last=android.os.SystemClock.elapsedRealtime()
        emit()
    }
    private fun cycle(now: Long) { cycles++; used++; level-=4; due=now+3000 }
    @Synchronized private fun tick() {
        val now=android.os.SystemClock.elapsedRealtime()
        if(day!=java.time.LocalDate.now()){ day=java.time.LocalDate.now(); used=0 }
        if(activeId!=null && now>=due){
            if(cycles>=3 || used>=12 || level<4) finish() else cycle(now)
        }
        emit()
    }
    @Synchronized private fun begin(id: String, nonce: String) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(activeId!=null || nonce!=key || level<4 || used>=12 || (last!=0L && now-last<10000)){
            queue.trySend(Signal.Reply(id,"Rechazado",0)); return
        }
        activeId=id; cycles=0; key=UUID.randomUUID().toString(); cycle(now); emit()
    }
    override suspend fun startFeed(id: String, nonce: String) = begin(id,nonce)
    override suspend fun stopFeed() { synchronized(this){ if(activeId!=null) finish() } }
    override fun close() { scope.cancel(); queue.close() }
}
