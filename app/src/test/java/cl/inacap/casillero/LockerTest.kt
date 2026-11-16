package cl.inacap.casillero
import org.junit.Assert.*
import org.junit.Test
class LockerTest {
 private var time=10000L
 private val l=Locker { time }
 private fun link() { l.bluetoothHeartbeat(); l.wifiHeartbeat() }
 @Test fun requiresLocalLogin() { link(); assertFalse(l.openLocally(false)) }
 @Test fun needsBothLinks() { l.bluetoothHeartbeat(); assertFalse(l.openLocally(true)) }
 @Test fun localOpening() { link(); assertTrue(l.openLocally(true)); assertTrue(l.opened) }
 @Test fun autoClose() { link(); l.openLocally(true); time+=5000; link(); l.tick(); assertFalse(l.opened) }
 @Test fun lossCloses() { link(); l.openLocally(true); l.disconnected(); assertFalse(l.opened) }
 @Test fun staleCloses() { link(); l.openLocally(true); time+=5000; l.tick(); assertFalse(l.opened) }
 @Test fun thresholdBoundary() { l.setDistance(15); assertFalse(l.alarm()); l.setDistance(14); assertTrue(l.alarm()) }
 @Test(expected=IllegalArgumentException::class) fun rejectsDistance() { l.setDistance(-1) }
 @Test(expected=IllegalArgumentException::class) fun rejectsThreshold() { l.setThreshold(51) }
 @Test fun reconnectDoesNotOpen() { link(); l.openLocally(true); l.disconnected(); link(); l.tick(); assertFalse(l.opened) }
 @Test fun freshAt4999ms() { link(); time+=4999; assertTrue(l.fresh()); time++; assertFalse(l.fresh()) }
 @Test fun alarmDuringSecureClose() { link(); l.setDistance(1); l.openLocally(true); assertFalse(l.alarm()); l.disconnected(); assertTrue(l.alarm()) }
}
