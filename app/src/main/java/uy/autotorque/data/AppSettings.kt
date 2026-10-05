package uy.autotorque.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import uy.autotorque.torque.Sensor

/**
 * Preferencias de la app. Se guardan también los nombres de los sensores elegidos,
 * así el auto puede dibujar el tablero aunque Torque todavía no esté conectado.
 */
object AppSettings {

    private const val FILE = "torque_auto"
    private const val KEY_SENSORS = "selected_sensors"
    private const val KEY_LIST_LAYOUT = "list_layout"
    private const val KEY_REFRESH_MS = "refresh_ms"

    val REFRESH_OPTIONS_MS = listOf(500L, 1000L, 2000L, 3000L)
    const val DEFAULT_REFRESH_MS = 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Texto crudo guardado; sirve para detectar cambios sin volver a parsear. */
    fun selectedSensorsRaw(context: Context): String =
        prefs(context).getString(KEY_SENSORS, "") ?: ""

    /** Sensores elegidos, en el orden en que se muestran. Lista vacía = modo automático. */
    fun selectedSensors(context: Context): List<Sensor> = parseSensors(selectedSensorsRaw(context))

    fun parseSensors(raw: String): List<Sensor> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sensor(
                    id = o.getString("id"),
                    longName = o.optString("long"),
                    shortName = o.optString("short"),
                    unit = o.optString("unit"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setSelectedSensors(context: Context, sensors: List<Sensor>) {
        val arr = JSONArray()
        sensors.distinctBy { it.id }.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("long", s.longName)
                    .put("short", s.shortName)
                    .put("unit", s.unit)
            )
        }
        prefs(context).edit().putString(KEY_SENSORS, if (sensors.isEmpty()) "" else arr.toString()).apply()
    }

    fun useListLayout(context: Context): Boolean = prefs(context).getBoolean(KEY_LIST_LAYOUT, false)

    fun setUseListLayout(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LIST_LAYOUT, value).apply()
    }

    fun refreshMs(context: Context): Long =
        prefs(context).getLong(KEY_REFRESH_MS, DEFAULT_REFRESH_MS)
            .takeIf { it in REFRESH_OPTIONS_MS } ?: DEFAULT_REFRESH_MS

    fun setRefreshMs(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_REFRESH_MS, value).apply()
    }
}
