package cl.marea.data

import android.content.Context
import cl.marea.domain.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

class Accounts(private val context: Context) {
    val configured get() = FirebaseApp.getApps(context).isNotEmpty()
    val uid get() = if (configured) FirebaseAuth.getInstance().currentUser?.uid else null
    suspend fun enter(email: String, password: String, register: Boolean) {
        check(configured) { "Falta app/google-services.json. Puedes entrar a Simulación." }
        val auth = FirebaseAuth.getInstance()
        if (register) auth.createUserWithEmailAndPassword(email,password).await()
        else auth.signInWithEmailAndPassword(email,password).await()
    }
    fun exit() { if (configured) FirebaseAuth.getInstance().signOut() }
}
class CloudJournal(uid: String): Journal {
    private val collection = FirebaseFirestore.getInstance().collection("users").document(uid).collection("feedings")
    override val entries = callbackFlow {
        val listener = collection.orderBy("time", Query.Direction.DESCENDING).limit(100).addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else trySend(snapshot.orEmptyEntries())
        }
        awaitClose { listener.remove() }
    }
    private fun com.google.firebase.firestore.QuerySnapshot?.orEmptyEntries() = this?.documents.orEmpty().map {
        Entry(it.id,it.getLong("time") ?: 0,(it.getLong("portions") ?: 0).toInt(),it.getString("result").orEmpty())
    }
    override suspend fun save(entry: Entry) {
        collection.document(entry.id).set(mapOf("time" to entry.time,"portions" to entry.portions,"result" to entry.result)).await()
    }
}
class DemoJournal(context: Context): Journal {
    private val prefs = context.getSharedPreferences("demo-journal",Context.MODE_PRIVATE)
    private val list = MutableStateFlow(read())
    override val entries = list.asStateFlow()
    private fun read(): List<Entry> {
        val rows = JSONArray(prefs.getString("entries","[]"))
        return (0 until rows.length()).map { val r = rows.getJSONArray(it); Entry(r.getString(0),r.getLong(1),r.getInt(2),r.getString(3)) }
    }
    override suspend fun save(entry: Entry) {
        val rows = (listOf(entry) + list.value.filter { it.id != entry.id }).sortedByDescending { it.time }.take(100)
        val json = JSONArray()
        rows.forEach { json.put(JSONArray(listOf(it.id,it.time,it.portions,it.result))) }
        prefs.edit().putString("entries",json.toString()).apply()
        list.value = rows
    }
}
