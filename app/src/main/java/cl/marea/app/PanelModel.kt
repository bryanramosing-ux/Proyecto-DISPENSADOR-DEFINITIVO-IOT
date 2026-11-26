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
                    is Signal.Reply -> pending?.takeIf { it.id==event.id }?.let { finish(it.copy(portions=event.cycles),event.result) }
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
    fun toggleFeed() {
        val target=device ?: return
        val current=state.value
        if(current.pending || current.reading?.active==true){
            session.launch {
                try { target.stopFeed(); note("Parada enviada · esperando estado") }
                catch(e: Exception){ note("Parada sin confirmar · revisa la conexión") }
            }
            return
        }
        val reading=current.reading ?: return
        if(!current.connected || reading.level<4) return
        val entry=Entry(UUID.randomUUID().toString(),System.currentTimeMillis(),0,"Pendiente")
        pending=entry
        change { it.copy(pending=true,message="Inicio solicitado") }
        val store=journal ?: return
        session.launch {
            try { store.save(entry) }
            catch(e: CancellationException){ throw e }
            catch(e: Exception){ note("Revisa los permisos del historial") }
        }
        session.launch {
            try { target.startFeed(entry.id,reading.nonce) }
            catch(e: CancellationException){ throw e }
            catch(e: Exception){ note("Envío sin confirmar") }
            delay(12000)
            if(pending?.id==entry.id) finish(entry,"Sin confirmación")
        }
    }
    private fun finish(entry: Entry, result: String) {
        pending=null
        change { it.copy(pending=false,message=result) }
        val store=journal ?: return
        session.launch {
            try { store.save(entry.copy(result=result)) }
            catch(e: CancellationException){ throw e }
            catch(e: Exception){ note("$result · revisa el historial") }
        }
    }
    private fun reset() { session.cancel(); device?.close(); device=null; pending=null; session=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate) }
    fun exit() { reset(); accounts.exit(); state.value=Panel() }
    override fun onCleared() { session.cancel(); device?.close() }
}
