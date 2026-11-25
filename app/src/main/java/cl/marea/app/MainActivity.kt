package cl.marea.app

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.sin

private val Ink = Color(0xff071b26)
private val Mint = Color(0xff8ff5d3)
private val Foam = Color(0xffe3f6f2)
class MainActivity: ComponentActivity() {
    private val model: PanelModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Ink,surface=Ink,onSurface=Foam)) {
                val state by model.ui.collectAsState()
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xff205257),Ink,Color(0xff272542)))).safeDrawingPadding()) {
                    key(state.mode){ Screen(state,model) }
                }
            }
        }
    }
}
@Composable private fun Glass(content: @Composable ColumnScope.()->Unit) {
    val shape=RoundedCornerShape(25.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha=.14f),Color.White.copy(alpha=.035f))))
        .border(1.dp,Brush.linearGradient(listOf(Color.White.copy(alpha=.46f),Mint.copy(alpha=.08f))),shape)
        .padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}
@Composable private fun Field(label: String, value: String, secret: Boolean=false, update:(String)->Unit) {
    OutlinedTextField(value,update,Modifier.fillMaxWidth(),singleLine=true,label={ Text(label) },
        keyboardOptions=KeyboardOptions(keyboardType=if(secret) KeyboardType.Password else KeyboardType.Text),
        visualTransformation=if(secret) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun Gauge(level: Int?) {
    val phase by rememberInfiniteTransition(label="superficie").animateFloat(0f,6.28318f,
        infiniteRepeatable(tween(2500,easing=LinearEasing)),label="onda")
    Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(18.dp)).background(Ink),contentAlignment=Alignment.Center) {
        if(level != null && level >= 0) Canvas(Modifier.fillMaxSize()) {
            val y=size.height*(1f-level.coerceIn(0,100)/100f)
            val path=Path().apply {
                moveTo(0f,size.height)
                for (i in 0..80) { val t=i/80f; lineTo(t*size.width,y+sin(t*8f+phase)*5f) }
                lineTo(size.width,size.height); close()
            }
            drawPath(path,Brush.verticalGradient(listOf(Color(0xff429c9c),Color(0xff165565))))
        }
        Text(if(level == null || level<0) "Sin lectura" else "$level %",color=Foam,fontSize=40.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun Screen(s: Panel,m: PanelModel) {
    var tab by rememberSaveable { mutableStateOf("Tolva") }
    var portions by rememberSaveable { mutableIntStateOf(1) }
    var confirm by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("") }
    var id by rememberSaveable { mutableStateOf("equipo01") }
    var user by rememberSaveable { mutableStateOf("") }
    var brokerPass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("MAREA   /   DISPENSADOR",letterSpacing=3.sp,color=Mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
            Text(if(s.mode.isEmpty()) "Tu estación de alimento" else tab,color=Foam,fontSize=34.sp,fontWeight=FontWeight.Bold)
            if(s.mode.isEmpty()) {
                var mail by rememberSaveable { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                Glass {
                    Field("Correo",mail){ mail=it }
                    Field("Contraseña",pass,true){ pass=it }
                    Button({m.enter(mail,pass,false)},enabled=!s.busy){Text("Ingresar")}
                    TextButton({m.enter(mail,pass,true)},enabled=!s.busy){Text("Crear cuenta")}
                }
                OutlinedButton({m.start(true)},enabled=!s.busy){Text("Probar en simulación")}
            } else {
                Text(s.mode + "   ·   " + if(s.connected) "Telemetría activa" else "Sin telemetría",color=Mint)
                when(tab) {
                    "Tolva" -> {
                        Glass {
                            Text("Reserva de alimento",color=Foam)
                            Gauge(s.reading?.level)
                            Text(s.reading?.cm?.takeIf{it>=0}?.let{"Distancia frente al plato · $it cm"} ?: "Distancia frente al plato · sin lectura",color=Foam)
                        }
                        Glass {
                            Text("Porciones por ciclo",color=Foam)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){ (1..3).forEach {
                                FilterChip(portions==it,{portions=it},{Text(it.toString())})
                            } }
                            Button({confirm=true},enabled=s.connected && !s.pending && (s.reading?.level ?: 0)>0){Text(if(s.pending) "Esperando confirmación…" else "Dispensar")}
                        }
                    }
                    "Cámara" -> Glass {
                        val bitmap=remember(s.jpeg){s.jpeg?.let{BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap()}}
                        val context = LocalContext.current
                        var export by remember { mutableStateOf<ByteArray?>(null) }
                        var exportStatus by remember { mutableStateOf("") }
                        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
                            if(uri!=null) exportStatus = runCatching {
                                val bytes = requireNotNull(export)
                                requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                                "Fotografía guardada"
                            }.getOrElse { "Error al guardar la fotografía" }
                            export=null
                        }
                        if(bitmap!=null) Image(bitmap,"Último fotograma",Modifier.fillMaxWidth().height(220.dp))
                        else Text(if(s.mode=="Simulación") "La cámara estará disponible al conectar un equipo real." else "Solicita una imagen a la ESP32-CAM.",color=Foam)
                        Button({m.photo()},enabled=s.connected && s.mode!="Simulación"){Text("Tomar fotografía")}
                        if(bitmap!=null) TextButton({export=s.jpeg;save.launch("Marea-"+System.currentTimeMillis()+".jpg")}){Text("Guardar fotografía")}
                        if(exportStatus.isNotEmpty()) Text(exportStatus,color=Foam)
                        Row(verticalAlignment=Alignment.CenterVertically){Switch(live,{live=it},enabled=s.connected && s.mode!="Simulación"); Text("   Vista en directo",color=Foam)}
                        LaunchedEffect(live,s.connected){ while(live && s.connected){ m.photo(true);delay(3000) } }
                    }
                    "Historial" -> Glass {
                        if(s.entries.isEmpty()) Text("Los pedidos aparecerán aquí.",color=Foam)
                        s.entries.forEach { Text(DateFormat.getDateTimeInstance().format(Date(it.time)),color=Mint,fontSize=12.sp)
                            Text("${it.portions} porción(es) · ${it.result}",color=Foam); HorizontalDivider() }
                    }
                    "Ajustes" -> Glass {
                        if(s.mode=="Equipo real"){
                            Field("Host del broker TLS",host){host=it}
                            Field("ID del dispensador",id){id=it}
                            Field("Usuario MQTT",user){user=it}
                            Field("Contraseña MQTT",brokerPass,true){brokerPass=it}
                            Button({m.broker(host,id,user,brokerPass)},enabled=!s.pending && !s.busy){Text("Conectar equipo")}
                        } else Text("Equipo virtual · historial guardado en este teléfono.",color=Foam)
                        TextButton({m.exit()},enabled=!s.pending){Text("Salir de la sesión")}
                    }
                }
            }
            if(s.message.isNotBlank()) Text(s.message,color=Foam)
        }
        if(s.mode.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom=8.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha=.09f)),horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf("Tolva","Cámara","Historial","Ajustes").forEach {
                TextButton({tab=it}){ Text(it,color=if(it==tab) Mint else Foam,fontSize=12.sp) }
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Confirmar dispensación")},text={Text("Activar $portions porción(es) en ${s.mode.lowercase()}?")},confirmButton={TextButton({confirm=false;m.feed(portions)}){Text("Dispensar")}},dismissButton={TextButton({confirm=false}){Text("Cancelar")}})
}
