package uy.autotorque.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import uy.autotorque.torque.Sensor
import uy.autotorque.torque.TorqueConnection
import uy.autotorque.torque.Units

enum class TorqueStatus { NOT_INSTALLED, CONNECTING, NO_ECU, LIVE }

data class Reading(
    val sensor: Sensor,
    /** Valor ya formateado, o "—" si no hay dato. */
    val value: String,
    /** Unidad en la que está expresado [value]. */
    val unit: String,
)

sealed interface DashboardState {
    data object Loading : DashboardState

    data class Ready(
        val status: TorqueStatus,
        /** Vacía solo si todavía no sabemos qué sensores mostrar. */
        val readings: List<Reading>,
        val autoMode: Boolean,
    ) : DashboardState
}

/** Consultas puntuales a Torque (bloqueantes: llamarlas en un hilo de fondo). */
object TorqueRepository {

    fun sensorInfo(ids: List<String>): List<Sensor> {
        if (ids.isEmpty()) return emptyList()
        val info = TorqueConnection.call { it.getPIDInformation(ids.toTypedArray()) }
        return ids.mapIndexed { i, id -> Sensor.fromTorqueCsv(id, info?.getOrNull(i)) }
    }

    /** Todos los sensores que conoce Torque, ordenados por nombre. null = Torque no conectado. */
    fun allSensors(): List<Sensor>? {
        val ids = TorqueConnection.call { it.listAllPIDs() } ?: return null
        return sensorInfo(ids.filterNotNull()).sortedBy { it.longName.lowercase() }
    }

    fun activeSensorIds(): Set<String> =
        TorqueConnection.call { it.listActivePIDs() }?.filterNotNull()?.toSet().orEmpty()
}

object DashboardFeed {

    /** Mientras Torque arranca, mostramos "cargando" hasta este tiempo antes de dar un estado. */
    private const val STARTUP_GRACE_MS = 4000L
    private const val AUTO_RETRY_MS = 5000L

    /**
     * Emite el estado del tablero periódicamente.
     *
     * Para respetar los límites de Android Auto, la lista de sensores se mantiene
     * estable durante la sesión: solo cambian los valores (eso cuenta como "refresh").
     */
    fun states(context: Context, maxItems: Int): Flow<DashboardState> = flow {
        val app = context.applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        val preferredUnits = HashMap<String, String>()
        var lastRaw: String? = null
        var sensors: List<Sensor> = emptyList()
        var autoMode = false
        var lastAutoAttempt = 0L

        while (currentCoroutineContext().isActive) {
            // 1) Qué sensores mostrar (elegidos por el usuario, o automático)
            val raw = AppSettings.selectedSensorsRaw(app)
            if (raw != lastRaw) {
                lastRaw = raw
                val chosen = AppSettings.parseSensors(raw).take(maxItems)
                autoMode = chosen.isEmpty()
                sensors = chosen
                lastAutoAttempt = 0L
            }

            // 2) Estado de Torque
            val installed = TorqueConnection.isTorqueInstalled(app)
            if (installed && !TorqueConnection.isServiceConnected) TorqueConnection.retryBindIfNeeded()
            val ecu: Boolean? = TorqueConnection.call { it.isConnectedToECU() }
            val status = when {
                !installed -> TorqueStatus.NOT_INSTALLED
                ecu == null -> TorqueStatus.CONNECTING
                !ecu && !TorqueConnection.testModeActive -> TorqueStatus.NO_ECU
                else -> TorqueStatus.LIVE
            }
            if (ecu == null) preferredUnits.clear()

            // 3) Modo automático: usar los sensores activos en Torque. Se toman una vez
            //    y se congelan, para no cambiar la cantidad de ítems a cada rato.
            val now = SystemClock.elapsedRealtime()
            if (autoMode && sensors.isEmpty() && status == TorqueStatus.LIVE &&
                now - lastAutoAttempt >= AUTO_RETRY_MS
            ) {
                lastAutoAttempt = now
                val active = TorqueConnection.call { it.listActivePIDs() }?.filterNotNull().orEmpty()
                sensors = TorqueRepository.sensorInfo(active.take(maxItems))
            }

            // 4) Valores actuales
            val values: FloatArray? =
                if (status == TorqueStatus.LIVE && sensors.isNotEmpty()) {
                    TorqueConnection.call { it.getPIDValues(sensors.map { s -> s.id }.toTypedArray()) }
                } else null

            val readings = sensors.mapIndexed { i, s ->
                val target = preferredUnit(s.unit, preferredUnits)
                val rawValue = values?.getOrNull(i)
                val converted = rawValue?.let { Units.convert(it, s.unit, target) }
                when {
                    rawValue == null -> Reading(s, "—", displayUnit(s.unit, target))
                    converted != null -> Reading(s, Units.format(converted, target), target)
                    else -> Reading(s, Units.format(rawValue, s.unit), s.unit)
                }
            }

            // 5) Emitir
            val stillStarting = sensors.isEmpty() &&
                status != TorqueStatus.NOT_INSTALLED &&
                now - startedAt < STARTUP_GRACE_MS
            emit(
                if (stillStarting) DashboardState.Loading
                else DashboardState.Ready(status, readings, autoMode)
            )

            delay(AppSettings.refreshMs(app))
        }
    }.flowOn(Dispatchers.IO)

    /** Unidad preferida por el usuario en Torque (cacheada). Si no se puede saber, la cruda. */
    private fun preferredUnit(rawUnit: String, cache: HashMap<String, String>): String {
        if (rawUnit.isBlank()) return rawUnit
        cache[rawUnit]?.let { return it }
        val pref = TorqueConnection.call { it.getPreferredUnit(rawUnit) } ?: return rawUnit
        cache[rawUnit] = pref
        return pref
    }

    /** Sin valor, mostramos la unidad en la que se mostraría el valor si lo hubiera. */
    private fun displayUnit(rawUnit: String, target: String): String =
        if (Units.convert(1f, rawUnit, target) != null) target else rawUnit
}
