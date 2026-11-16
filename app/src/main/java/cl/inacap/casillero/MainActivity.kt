package cl.inacap.casillero

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.*
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.net.NetworkInterface

class MainActivity:AppCompatActivity() {
 private lateinit var vault:Vault
 private lateinit var events:EventStore
 private val locker=Locker { SystemClock.elapsedRealtime() }
 private val handler=Handler(Looper.getMainLooper())
 private var role:String?=null
 private var tls:TlsMonitor?=null
 private var bt:BluetoothPresence?=null
 private var tlsStarted=false
 private var btStarted=false
 private lateinit var layout:LinearLayout
 private var status:TextView?=null
 private var btStatus:TextView?=null
 private var openButton:MaterialButton?=null
 @Volatile private var lastRemote=0L
 @Volatile private var remoteText="Sin telemetría"
 private var lastOpen=false
 private var attemptsBusy=false
 private val ticker=object:Runnable { override fun run() {
  locker.tick()
  if(role=="TERMINAL") {
   if(lastOpen && !locker.opened) events.add("CIERRE","Automático o pérdida de enlace")
   lastOpen=locker.opened; status?.text=stateText(snapshot()); openButton?.isEnabled=locker.fresh()
  } else if(role=="SUPERVISOR") status?.text=if(SystemClock.elapsedRealtime()-lastRemote<5000) remoteText else "SIN DATOS VIGENTES\nÚltima lectura:\n$remoteText"
  handler.postDelayed(this,500)
 } }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
  vault=Vault(this); events=EventStore(this,vault); loginPage(); handler.post(ticker)
 }
 private fun page(title:String) {
  status=null; btStatus=null; openButton=null
  layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,28,32,32) }
  setContentView(ScrollView(this).apply { addView(layout) }); label(title,25f)
 }
 private fun label(text:String,size:Float=16f):TextView=TextView(this).apply { this.text=text; textSize=size; setPadding(0,12,0,12); this@MainActivity.layout.addView(this) }
 private fun input(hint:String,secret:Boolean=false):TextInputEditText {
  val box=TextInputLayout(this); box.hint=hint
  val edit=TextInputEditText(this); edit.setSingleLine(true)
  if(secret) edit.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
  box.addView(edit); layout.addView(box); return edit
 }
 private fun button(text:String,action:()->Unit):MaterialButton=MaterialButton(this).apply { this.text=text; this@MainActivity.layout.addView(this); setOnClickListener { runCatching(action).onFailure { message(it.message ?: "Error") } } }
 private fun message(text:String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
 private fun loginPage() {
  val setup=!vault.exists(); page(if(setup) "Crear cuenta local" else "Iniciar sesión")
  label("Casillero inteligente · Unidad 2")
  val user=input("Usuario (mínimo 3 caracteres)"); val password=input("Contraseña (mínimo 10 caracteres)",true)
  val choices=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Terminal B · operador local","Teléfono A · supervisor")) }
  if(setup) { label("El rol se fija en esta instalación durante la preparación del laboratorio."); layout.addView(choices) }
  val feedback=label("")
  val submit=button(if(setup) "Crear cuenta" else "Ingresar") {}
  submit.setOnClickListener {
   if(attemptsBusy) return@setOnClickListener
   attemptsBusy=true; submit.isEnabled=false
   val u=user.text.toString(); val p=password.text.toString(); val selected=if(choices.selectedItemPosition==0) "TERMINAL" else "SUPERVISOR"
   Thread {
    val result=runCatching { if(setup) vault.register(u,p,selected); vault.login(u,p) ?: error("Credenciales incorrectas") }
    runOnUiThread {
     attemptsBusy=false; submit.isEnabled=true; password.text?.clear()
     result.onSuccess { role=it; events.add("LOGIN","Acceso local correcto"); home() }.onFailure { feedback.text=it.message ?: "No fue posible ingresar" }
    }
   }.start()
  }
 }
 private fun snapshot():JSONObject=JSONObject().put("distance",locker.distance).put("threshold",locker.threshold).put("open",locker.opened).put("alarm",locker.alarm()).put("fresh",locker.fresh()).put("time",System.currentTimeMillis()).put("simulated",true)
 private fun stateText(j:JSONObject)="SIMULACIÓN · HC-SR04 y SG90\nDistancia: ${j.getInt("distance")} cm · Umbral: ${j.getInt("threshold")} cm\nCerradura: ${if(j.getBoolean("open")) "ABIERTA" else "CERRADA"}\nAlarma: ${if(j.getBoolean("alarm")) "ACTIVA" else "normal"}\nVigilancia: activa\nEnlaces vigentes: ${if(j.getBoolean("fresh")) "sí" else "no"}"
 private fun home() {
  page(if(role=="TERMINAL") "Terminal del casillero" else "Monitoreo del supervisor")
  status=label(if(role=="TERMINAL") stateText(snapshot()) else "Sin datos")
  if(role=="TERMINAL") terminalControls() else supervisorControls()
  label("Conexión Bluetooth de presencia",20f)
  btStatus=label("Sin conexión. Empareja ambos teléfonos desde Ajustes antes de continuar.")
  button("Seleccionar teléfono emparejado") { chooseBluetooth() }
  button("Ver historial local") { MaterialAlertDialogBuilder(this).setTitle("Últimos 15 eventos").setMessage(events.latest()).setPositiveButton("Cerrar",null).show() }
  button("Cerrar sesión y conexiones") { logout() }
 }
 private fun terminalControls() {
  label("Activador local",20f)
  label("Solo esta pantalla puede abrir. Requiere confirmación local y ambos enlaces vigentes.")
  openButton=button("Abrir localmente por 5 segundos") {
   MaterialAlertDialogBuilder(this).setTitle("Confirmar apertura local").setMessage("¿Estás frente al casillero y autorizas abrirlo?")
    .setNegativeButton("Cancelar",null).setPositiveButton("Abrir") { _,_ ->
     if(locker.openLocally(role=="TERMINAL")) { lastOpen=true; events.add("APERTURA","Confirmación local en B") }
     else message("Apertura bloqueada: inicia sesión y verifica los dos enlaces")
    }.show()
  }
  button("Cerrar ahora") { locker.close(); events.add("CIERRE","Acción local") }
  label("Simular sensor de distancia de 0 a 400 cm")
  layout.addView(Slider(this).apply { valueFrom=0f; valueTo=400f; stepSize=1f; value=locker.distance.toFloat(); addOnChangeListener { _,v,from -> if(from) locker.setDistance(v.toInt()) } })
  label("Umbral local de alerta de 5 a 50 cm")
  layout.addView(Slider(this).apply { valueFrom=5f; valueTo=50f; stepSize=1f; value=locker.threshold.toFloat(); addOnChangeListener { _,v,from -> if(from) locker.setThreshold(v.toInt()) } })
  label("Servidor Wi-Fi de solo lectura",20f)
  label("IP local: ${localIps()} · Puerto 8443. Misma red Wi-Fi o hotspot. Sin control por internet.")
  button("Iniciar servidor TLS") {
   check(!tlsStarted) { "Servidor ya iniciado" }
   val channel=TlsMonitor(); tls=channel
   val token=vault.get("monitorToken") ?: Vault.randomToken().also { vault.put("monitorToken",it) }
   channel.serve(token,{ snapshot() },{ locker.wifiHeartbeat() },{})
   tlsStarted=true; events.add("TLS","Servidor iniciado")
   MaterialAlertDialogBuilder(this).setTitle("Datos de vinculación privada")
    .setMessage("Copia en A, verificando directamente esta pantalla. No publiques estos valores.\n\nHUELLA SHA-256:\n${channel.fingerprint()}\n\nTOKEN:\n$token")
    .setPositiveButton("Cerrar",null).show()
  }
  button("Mostrar datos de vinculación") {
   check(tlsStarted) { "Inicia el servidor primero" }
   MaterialAlertDialogBuilder(this).setTitle("Vinculación privada").setMessage("${tls!!.fingerprint()}\n\n${vault.get("monitorToken")}").setPositiveButton("Cerrar",null).show()
  }
 }
 private fun supervisorControls() {
  label("Consulta Wi-Fi cifrada",20f); label("Este teléfono no tiene botones ni comandos de apertura.")
  val ip=input("IP del teléfono B (ejemplo 192.168.1.20)")
  val pin=input("Huella SHA-256 mostrada en B")
  val token=input("Token mostrado en B",true)
  ip.setText(vault.get("host") ?: ""); pin.setText(vault.get("pin") ?: ""); token.setText(vault.get("token") ?: "")
  button("Conectar monitoreo TLS") {
   check(!tlsStarted) { "Ya está conectado. Cierra sesión para cambiar los datos." }
   val h=ip.text.toString().trim(); val f=pin.text.toString().trim(); val t=token.text.toString().trim()
   require(h.isNotEmpty()); val channel=TlsMonitor(); tls=channel
   channel.poll(h,f,t,{ state ->
    val text=stateText(state); lastRemote=SystemClock.elapsedRealtime(); remoteText=text
    events.add("LECTURA",state.toString())
   },{ msg -> runOnUiThread { remoteText=msg; lastRemote=0 } })
   tlsStarted=true; vault.put("host",h); vault.put("pin",f); vault.put("token",t)
   message("Consultando por TLS cada segundo")
  }
 }
 @SuppressLint("MissingPermission")
 private fun chooseBluetooth() {
  if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
   requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),20); message("Concede permiso y pulsa otra vez"); return
  }
  check(!btStarted) { "Bluetooth ya iniciado. Cierra sesión para reconectar." }
  val adapter=(getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: error("Este dispositivo no tiene Bluetooth")
  check(adapter.isEnabled) { "Activa Bluetooth en Ajustes" }
  val devices=adapter.bondedDevices.toList(); check(devices.isNotEmpty()) { "Empareja primero desde Ajustes de Android" }
  MaterialAlertDialogBuilder(this).setTitle("Selecciona el otro teléfono")
   .setItems(devices.map { "${it.name ?: "Dispositivo"} · ${it.address}" }.toTypedArray()) { _,index ->
    val link=BluetoothPresence(adapter); bt=link; btStarted=true
    if(role=="TERMINAL") {
     btStatus?.text="Esperando presencia de ${devices[index].name}"
     link.listen(devices[index].address,{ locker.bluetoothHeartbeat(); runOnUiThread { btStatus?.text="Presencia Bluetooth recibida" } },{ locker.disconnected(); runOnUiThread { btStatus?.text="Sin presencia Bluetooth: cierre seguro" } })
    } else link.connect(devices[index].address,{ msg -> runOnUiThread { btStatus?.text=msg } },{})
   }.show()
 }
 private fun localIps():String=runCatching { java.util.Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { java.util.Collections.list(it.inetAddresses) }.filter { !it.isLoopbackAddress && it is java.net.Inet4Address }.joinToString { it.hostAddress ?: "" } }.getOrDefault("Ver Ajustes de Wi-Fi")
 private fun logout() { tls?.stop(); tls=null; bt?.stop(); bt=null; tlsStarted=false; btStarted=false; locker.disconnected(); role=null; lastRemote=0; loginPage() }
 override fun onStop() { super.onStop(); if(role!=null) logout() }
 override fun onDestroy() { handler.removeCallbacks(ticker); tls?.stop(); bt?.stop(); events.close(); super.onDestroy() }
}
