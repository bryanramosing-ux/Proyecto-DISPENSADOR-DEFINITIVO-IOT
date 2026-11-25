package cl.marea.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.marea.data.*
import cl.marea.domain.*
import cl.marea.hardware.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class Panel(val mode: String = "", val message: String = "", val reading: Reading? = null,
                 val entries: List<Entry> = emptyList(), val pending: Boolean = false,
                 val jpeg: ByteArray? = null, val connected: Boolean = false, val busy: Boolean = false)
class PanelModel(app: Application): AndroidViewModel(app) {
    private val accounts = Accounts(app)
    private val state = MutableStateFlow(Panel())
    val ui = state.asStateFlow()
    private var journal: Journal? = null
    private var device: Device? = null
    private var session = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var deviceJob: Job? = null
    private var pending: Entry? = null
    private var seen = 0L
    private fun change(f: (Panel)->Panel) { state.update(f) }
    private fun note(text: String) = change { it.copy(message = text) }
    init { accounts.uid?.let { start(false) } }
    fun enter(email: String, pass: String, register: Boolean) {
        if (state.value.busy) return
        change { it.copy(busy = true) }
        viewModelScope.launch {
            try { accounts.enter(email.trim(),pass,register); start(false) }
            catch (e: Exception) { change { it.copy(busy=false,message=e.localizedMessage ?: "Revisa tu cuenta") } }
        }
    }
    fun start(demo: Boolean) {
        reset()
        journal = if (demo) DemoJournal(getApplication()) else CloudJournal(requireNotNull(accounts.uid))
        state.value = Panel(mode = if (demo) "Simulación" else "Equipo real", message = if(demo) "Equipo virtual disponible" else "Configura el broker en Ajustes")
        session.launch { journal!!.entries.catch { note("Historial: " + it.localizedMessage) }.collect { rows -> change { it.copy(entries=rows) } } }
        if (demo) attach(Simulator())
    }
    fun broker(host: String, id: String, user: String, password: String) {
        if (state.value.mode != "Equipo real" || state.value.pending || state.value.busy) return
        try { attach(BrokerDevice(host.trim(),id.trim(),user,password)) }
        catch (e: Exception) { note(e.localizedMessage ?: "Configuración inválida") }
    }
    private fun attach(next: Device) {
        deviceJob?.cancel(); device?.close(); device = next
        change { it.copy(connected=false,reading=null,jpeg=null,busy=true,message="Conectando…") }
        deviceJob = session.launch {
            launch { next.events.collect { event ->
                when(event) {
                    is Signal.Status -> { seen=SystemClock.elapsedRealtime(); change { it.copy(reading=event.reading,connected=true) } }
                    is Signal.Photo -> change { it.copy(jpeg=event.jpeg,message="Fotografía recibida") }
                    is Signal.Link -> { if(event.message.startsWith("Broker")) change { it.copy(connected=false,reading=null) }; note(event.message) }
                    is Signal.Reply -> pending?.takeIf { it.id==event.id }?.let { finish(it,event.result) }
                }
            } }
            try {
                next.connect()
                change { it.copy(busy=false,message="Conectado · esperando lectura del equipo") }
                while(isActive) {
                    delay(2000)
                    if(state.value.connected && SystemClock.elapsedRealtime()-seen>9000)
                        change { it.copy(connected=false,reading=null,message="Telemetría vencida") }
                }
            } catch(e: CancellationException){ throw e }
            catch(e: Exception){ change { it.copy(busy=false,message=e.localizedMessage ?: "Falló MQTT") }; next.close() }
        }
    }
    fun feed(n: Int) {
        val current = state.value
        val nonce = current.reading?.nonce ?: return
        if (!current.connected || current.pending || n !in 1..3 || current.reading.level <= 0) return
        val entry = Entry(UUID.randomUUID().toString(),System.currentTimeMillis(),n,"Pendiente")
        pending=entry
        change { it.copy(pending=true,message="Registrando pedido…") }
        session.launch {
            try {
                withTimeout(8000) { journal!!.save(entry) }
                device!!.feed(entry.id,n,nonce)
                delay(9000)
            } catch(e: TimeoutCancellationException) { /* El registro o ACK puede llegar después. */ }
            catch(e: CancellationException){ throw e }
            catch(e: Exception){ note(e.localizedMessage ?: "Falló el pedido") }
            if(pending?.id == entry.id) finish(entry,"Sin confirmación")
        }
    }
    private suspend fun finish(entry: Entry, result: String) {
        pending=null
        change { it.copy(pending=false,message=result) }
        try { withTimeout(7000){ journal!!.save(entry.copy(result=result)) } }
        catch(e: TimeoutCancellationException){ note("$result · historial pendiente de sincronizar") }
        catch(e: CancellationException){ throw e }
        catch(e: Exception){ note("$result · revisa los permisos del historial") }
    }
    fun photo(live: Boolean=false) { session.launch { try { device?.capture(live) } catch(e: Exception){ note("No se pudo pedir la foto") } } }
    private fun reset() { session.cancel(); device?.close(); device=null; pending=null; session=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate) }
    fun exit() { reset(); accounts.exit(); state.value=Panel() }
    override fun onCleared() { session.cancel(); device?.close() }
}
