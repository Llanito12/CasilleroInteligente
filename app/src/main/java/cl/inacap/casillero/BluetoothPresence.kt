package cl.inacap.casillero
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothServerSocket
import java.util.UUID

/** Presence only; deliberately no actuator protocol. */
@SuppressLint("MissingPermission")
class BluetoothPresence(private val adapter:BluetoothAdapter) {
 private val uuid=UUID.fromString("a7c762d1-a945-40e0-9a31-078502c9d871")
 @Volatile private var running=false
 private var socket:BluetoothSocket?=null
 private var server:BluetoothServerSocket?=null
 fun listen(allowed:String,onBeat:()->Unit,onEnd:()->Unit) {
  stop(); running=true
  Thread {
   try {
    server=adapter.listenUsingRfcommWithServiceRecord("CasilleroPresence",uuid)
    while(running) {
     val s=server!!.accept()
     if(s.remoteDevice.address!=allowed) { s.close(); continue }
     socket=s
     try { while(running) { check(TlsMonitor.readLineBounded(s.inputStream)=="PRESENCE"); onBeat() } }
     finally { s.close(); onEnd() }
    }
   } catch(_:Exception) { onEnd() }
  }.start()
 }
 fun connect(address:String,onStatus:(String)->Unit,onEnd:()->Unit) {
  stop(); running=true
  Thread {
   try {
    val s=adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(uuid); socket=s; s.connect()
    onStatus("Bluetooth conectado: presencia local, sin órdenes")
    while(running) { s.outputStream.write("PRESENCE\n".toByteArray()); s.outputStream.flush(); Thread.sleep(1000) }
   } catch(_:Exception) { onStatus("Bluetooth desconectado") }
   finally { runCatching { socket?.close() }; onEnd() }
  }.start()
 }
 fun stop() { running=false; runCatching { socket?.close() }; runCatching { server?.close() }; socket=null; server=null }
}
