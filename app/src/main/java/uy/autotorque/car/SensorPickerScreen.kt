package uy.autotorque.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.autotorque.data.AppSettings
import uy.autotorque.data.TorqueRepository
import uy.autotorque.torque.Sensor

/**
 * Elegir sensores desde la pantalla del auto (solo detenido).
 * Orden: los ya elegidos, después los activos en Torque, después el resto.
 * Si no dejás ninguno marcado, el tablero vuelve al modo automático.
 */
class SensorPickerScreen(carContext: CarContext) : Screen(carContext) {

    /** null = cargando */
    private var sensors: List<Sensor>? = null
    private var loadFailed = false

    /** Elegidos, en el orden en que se marcaron (es el orden del tablero). */
    private val selected = LinkedHashMap<String, Sensor>()

    init {
        AppSettings.selectedSensors(carContext).forEach { selected[it.id] = it }

        lifecycleScope.launch {
            val (all, active) = withContext(Dispatchers.IO) {
                TorqueRepository.allSensors() to TorqueRepository.activeSensorIds()
            }
            if (all == null) {
                loadFailed = true
                sensors = emptyList()
            } else {
                val chosen = selected.keys.mapNotNull { id -> all.firstOrNull { it.id == id } }
                val rest = all.filter { it.id !in selected }
                sensors = chosen + rest.filter { it.id in active } + rest.filter { it.id !in active }
            }
            invalidate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("Sensores en pantalla")
            .setHeaderAction(Action.BACK)

        val list = sensors ?: return builder.setLoading(true).build()

        val items = ItemList.Builder()
        if (list.isEmpty()) {
            items.setNoItemsMessage(
                if (loadFailed) "No se pudo leer la lista. ¿Está abierto Torque Pro?"
                else "Torque no informó sensores"
            )
        }
        // ListTemplate admite hasta 100 filas; con el auto en marcha el sistema muestra menos.
        for (s in list.take(100)) {
            val subtitle = buildString {
                if (s.unit.isNotBlank()) append(s.unit)
                val pos = selected.keys.indexOf(s.id)
                if (pos >= 0) {
                    if (isNotEmpty()) append(" · ")
                    append("posición ${pos + 1}")
                }
            }
            val row = Row.Builder()
                .setTitle(s.longName.ifBlank { s.label })
                .setToggle(
                    Toggle.Builder { checked -> onToggle(s, checked) }
                        .setChecked(s.id in selected)
                        .build()
                )
            if (subtitle.isNotEmpty()) row.addText(subtitle)
            items.addItem(row.build())
        }
        return builder.setSingleList(items.build()).build()
    }

    private fun onToggle(sensor: Sensor, checked: Boolean) {
        if (checked) selected[sensor.id] = sensor else selected.remove(sensor.id)
        AppSettings.setSelectedSensors(carContext, selected.values.toList())
        invalidate()
    }
}
