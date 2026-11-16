package cl.inacap.casillero

/** Pure domain model. No remote command can invoke opening. Times are monotonic. */
class Locker(private val now: () -> Long) {
 var distance = 100; private set
 var threshold = 15; private set
 var opened = false; private set
 private var closeAt = 0L
 private var btAt: Long? = null
 private var wifiAt: Long? = null
 @Synchronized fun bluetoothHeartbeat() { btAt = now() }
 @Synchronized fun wifiHeartbeat() { wifiAt = now() }
 @Synchronized fun fresh(): Boolean = listOf(btAt, wifiAt).all { it != null && now() - it < 5000 }
 @Synchronized fun setDistance(value: Int) { require(value in 0..400); distance = value }
 @Synchronized fun setThreshold(value: Int) { require(value in 5..50); threshold = value }
 @Synchronized fun openLocally(authenticated: Boolean): Boolean {
  tick()
  if (!authenticated || !fresh()) return false
  opened = true; closeAt = now() + 5000; return true
 }
 @Synchronized fun close() { opened = false; closeAt = 0 }
 @Synchronized fun disconnected() { btAt = null; wifiAt = null; close() }
 @Synchronized fun tick() { if (!fresh() || now() >= closeAt) close() }
 @Synchronized fun alarm(): Boolean = !opened && distance < threshold
}
