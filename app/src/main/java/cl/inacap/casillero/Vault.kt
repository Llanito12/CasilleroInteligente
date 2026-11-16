package cl.inacap.casillero

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class Vault(context: Context) {
 private val prefs = context.getSharedPreferences("protected", Context.MODE_PRIVATE)
 private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
 private val alias = "locker.aes.v1"
 private fun key(): SecretKey {
  if (!ks.containsAlias(alias)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
  }.generateKey()
  return ks.getKey(alias, null) as SecretKey
 }
 fun seal(text: String): String {
  val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key())
  return b64(c.iv) + ":" + b64(c.doFinal(text.toByteArray(Charsets.UTF_8)))
 }
 fun unseal(value: String): String {
  val a=value.split(":"); require(a.size==2)
  val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,decode(a[0])))
  return String(c.doFinal(decode(a[1])),Charsets.UTF_8)
 }
 fun put(name: String, text: String) { check(prefs.edit().putString(name,seal(text)).commit()) }
 fun get(name: String): String? = prefs.getString(name,null)?.let { unseal(it) }
 fun exists() = prefs.contains("account")
 fun register(user: String, password: String, role: String) {
  require(!exists()); require(user.trim().length>=3 && password.length>=10)
  require(role in listOf("TERMINAL","SUPERVISOR"))
  val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
  put("account",org.json.JSONObject().put("user",user.trim()).put("role",role).put("salt",b64(salt)).put("hash",b64(derive(password,salt))).toString())
 }
 @Synchronized fun login(user: String,password: String): String? {
  val locked=get("lockUntil")?.toLongOrNull() ?: 0
  check(System.currentTimeMillis()>=locked) { "Bloqueado temporalmente. Espera 30 segundos." }
  val a=org.json.JSONObject(get("account") ?: return null)
  val equal=MessageDigest.isEqual(derive(password,decode(a.getString("salt"))),decode(a.getString("hash")))
  if (user.trim()==a.getString("user") && equal) { put("failures","0"); return a.getString("role") }
  val failures=(get("failures")?.toIntOrNull() ?: 0)+1
  if (failures>=5) { put("lockUntil",(System.currentTimeMillis()+30000).toString()); put("failures","0") }
  else put("failures",failures.toString())
  return null
 }
 companion object {
  fun b64(b: ByteArray): String=Base64.encodeToString(b,Base64.NO_WRAP)
  fun decode(s: String): ByteArray=Base64.decode(s,Base64.NO_WRAP)
  fun randomToken(): String=ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
  private fun derive(p:String,s:ByteArray):ByteArray {
   val spec=PBEKeySpec(p.toCharArray(),s,210000,256)
   return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
  }
 }
}
