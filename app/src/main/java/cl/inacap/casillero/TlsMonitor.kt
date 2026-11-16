package cl.inacap.casillero

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

/** Read-only TLS protocol. The sole accepted operation is GET. */
class TlsMonitor {
 @Volatile private var running=false
 private var server:SSLServerSocket?=null
 private var active:Socket?=null
 private val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
 private val alias="locker.tls.ec.v2"
 private fun ensureCertificate() {
  if (!ks.containsAlias(alias)) KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
   initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
    .setCertificateSubject(X500Principal("CN=Casillero local"))
    .setCertificateNotBefore(Date(System.currentTimeMillis()-86400000L))
    .setCertificateNotAfter(Date(System.currentTimeMillis()+31536000000L)).build())
  }.generateKeyPair()
 }
 fun fingerprint():String { ensureCertificate(); return digest(ks.getCertificate(alias).encoded) }
 fun serve(token:String,state:()->JSONObject,heartbeat:()->Unit,error:(String)->Unit) {
  stop(); ensureCertificate()
  val manager=object:X509ExtendedKeyManager() {
   override fun getPrivateKey(a:String?)=ks.getKey(alias,null) as PrivateKey
   override fun getCertificateChain(a:String?)=arrayOf(ks.getCertificate(alias) as X509Certificate)
   override fun getClientAliases(k:String?,p:Array<out java.security.Principal>?)=null
   override fun chooseClientAlias(k:Array<out String>?,p:Array<out java.security.Principal>?,s:Socket?)=null
   override fun getServerAliases(k:String?,p:Array<out java.security.Principal>?)=if(k=="EC") arrayOf(alias) else null
   override fun chooseServerAlias(k:String?,p:Array<out java.security.Principal>?,s:Socket?)=if(k=="EC") alias else null
  }
  val context=SSLContext.getInstance("TLSv1.2").apply { init(arrayOf(manager),null,SecureRandom()) }
  server=(context.serverSocketFactory.createServerSocket(8443) as SSLServerSocket).apply { enabledProtocols=arrayOf("TLSv1.2") }
  running=true
  Thread {
   while(running) try {
    (server!!.accept() as SSLSocket).use { sock ->
     active=sock; sock.soTimeout=3000
     sock.startHandshake()
     val request=JSONObject(readLineBounded(sock.getInputStream()))
     val authorized=MessageDigest.isEqual(request.optString("token").toByteArray(),token.toByteArray())
     val reply=if(authorized && request.optString("op")=="GET") { heartbeat(); state() } else JSONObject().put("error","DENIED")
     sock.getOutputStream().write((reply.toString()+"\n").toByteArray()); sock.getOutputStream().flush()
    }
   } catch(e:Exception) {
    if(running) {
     android.util.Log.w("CasilleroTLS","Terminal B: ${e.javaClass.simpleName}: ${e.message}")
     error("Solicitud TLS rechazada o conexión interrumpida")
    }
   }
  }.start()
 }
 fun poll(host:String,pin:String,token:String,onState:(JSONObject)->Unit,onError:(String)->Unit) {
  stop(); val normalized=pin.lowercase().replace(":","").replace(" ","")
  require(normalized.matches(Regex("[0-9a-f]{64}"))) { "Huella SHA-256 inválida" }
  require(token.matches(Regex("[0-9a-f]{32}"))) { "Token inválido" }
  val trust=object:X509TrustManager {
   override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
   override fun checkClientTrusted(c:Array<out X509Certificate>?,a:String?) { throw java.security.cert.CertificateException("No disponible") }
   override fun checkServerTrusted(c:Array<out X509Certificate>?,a:String?) {
    require(!c.isNullOrEmpty()); c[0].checkValidity()
    if(digest(c[0].encoded)!=normalized) throw java.security.cert.CertificateException("Huella no coincide")
   }
  }
  val context=SSLContext.getInstance("TLSv1.2").apply { init(null,arrayOf(trust),SecureRandom()) }; running=true
  Thread {
   while(running) {
    try {
     (context.socketFactory.createSocket() as SSLSocket).use { sock ->
      active=sock; sock.soTimeout=3000; sock.enabledProtocols=arrayOf("TLSv1.2")
      sock.connect(InetSocketAddress(host,8443),3000); sock.startHandshake()
      val request=JSONObject().put("op","GET").put("token",token)
      sock.getOutputStream().write((request.toString()+"\n").toByteArray()); sock.getOutputStream().flush()
      val response=JSONObject(readLineBounded(sock.getInputStream())); check(!response.has("error")) { "Acceso denegado" }; onState(response)
     }
    } catch(e:Exception) {
     if(running) {
      android.util.Log.w("CasilleroTLS","Supervisor A: ${e.javaClass.simpleName}: ${e.message}")
      onError(when {
       generateSequence<Throwable>(e) { it.cause }.any { it.message=="Huella no coincide" } ->
        "La huella no coincide con el certificado de B."
       e is SSLHandshakeException -> "Falló el inicio de TLS. Revisa el registro CasilleroTLS."
       e is java.net.ConnectException || e is java.net.SocketTimeoutException ->
        "Sin respuesta de B. Revisa el servidor y la redirección del puerto 8443."
       e.message=="Acceso denegado" -> "El servidor rechazó el token."
       else -> "Sin datos vigentes. Revisa el registro CasilleroTLS."
      })
     }
    }
    try { Thread.sleep(1000) } catch(_:InterruptedException) { break }
   }
  }.start()
 }
 fun stop() { running=false; runCatching { active?.close() }; runCatching { server?.close() }; active=null; server=null }
 companion object {
  fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  fun readLineBounded(input:InputStream):String {
   val out=java.io.ByteArrayOutputStream()
   repeat(4096) { val b=input.read(); if(b<0) throw java.io.EOFException(); if(b==10) return out.toString("UTF-8"); out.write(b) }
   throw java.io.IOException("Mensaje demasiado largo")
  }
 }
}
