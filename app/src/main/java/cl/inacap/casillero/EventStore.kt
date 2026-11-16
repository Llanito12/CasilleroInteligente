package cl.inacap.casillero
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class EventStore(c: Context, private val vault: Vault): SQLiteOpenHelper(c,"events.db",null,1) {
 override fun onCreate(db:SQLiteDatabase) { db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)") }
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) = Unit
 @Synchronized fun add(kind:String,data:String) {
  val event=JSONObject().put("time",System.currentTimeMillis()).put("kind",kind).put("data",data)
  writableDatabase.insertOrThrow("events",null,ContentValues().apply { put("payload",vault.seal(event.toString())) })
  writableDatabase.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 500)")
 }
 @Synchronized fun latest():String {
  val out=StringBuilder()
  readableDatabase.rawQuery("SELECT payload FROM events ORDER BY id DESC LIMIT 15",null).use { c ->
   while(c.moveToNext()) { val j=JSONObject(vault.unseal(c.getString(0))); out.append(java.util.Date(j.getLong("time"))).append(" | ").append(j.getString("kind")).append(" | ").append(j.getString("data")).append("\n") }
  }
  return out.toString().ifEmpty { "Sin eventos" }
 }
}
