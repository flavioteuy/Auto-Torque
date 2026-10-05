package uy.autotorque.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import uy.autotorque.data.AppSettings
import uy.autotorque.data.DashboardFeed
import uy.autotorque.data.DashboardState
import uy.autotorque.data.Reading
import uy.autotorque.data.TorqueStatus
import uy.autotorque.torque.iconKind

/**
 * Tablero en la pantalla del auto.
 *
 * Reglas de Android Auto que este código respeta a propósito:
 *  - Siempre devuelve el mismo tipo de plantilla (mosaico o lista, según la preferencia).
 *  - El título de la plantilla y el título de cada ítem (nombre del sensor) no cambian;
 *    el valor va en el texto secundario. Así cada actualización cuenta como "refresh"
 *    y no consume el cupo de 5 plantillas por tarea.
 */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private val icons = CarIcons(carContext)
    private val useList = AppSettings.useListLayout(carContext)
    private val maxItems = contentLimit()
    private var state: DashboardState = DashboardState.Loading

    init {
        lifecycleScope.launch {
            // Solo lee Torque mientras la pantalla está visible.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DashboardFeed.states(carContext, maxItems).collect { newState ->
                    // Una vez que hay datos, nunca volver a "cargando".
                    if (newState is DashboardState.Loading && state is DashboardState.Ready) return@collect
                    if (newState != state) {
                        state = newState
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template = if (useList) listTemplate() else gridTemplate()

    @Suppress("DEPRECATION") // setTitle/setHeaderAction/setActionStrip funcionan en todos los autos; setHeader exige API de auto 7
    private fun gridTemplate(): Template {
        val builder = GridTemplate.Builder()
            .setTitle(TITLE)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip())

        val s = state
        if (s !is DashboardState.Ready) return builder.setLoading(true).build()

        val items = ItemList.Builder()
        if (s.readings.isEmpty()) {
            items.addItem(
                GridItem.Builder()
                    .setTitle(STATUS_TITLE)
                    .setText(statusMessage(s))
                    .setImage(icons.app, GridItem.IMAGE_TYPE_ICON)
                    .build()
            )
        } else {
            for (r in s.readings) {
                items.addItem(
                    GridItem.Builder()
                        .setTitle(r.sensor.label)
                        .setText(valueText(r, s.status))
                        .setImage(icons.forKind(r.sensor.iconKind()), GridItem.IMAGE_TYPE_ICON)
                        .build()
                )
            }
        }
        return builder.setSingleList(items.build()).build()
    }

    @Suppress("DEPRECATION")
    private fun listTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(TITLE)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip())

        val s = state
        if (s !is DashboardState.Ready) return builder.setLoading(true).build()

        val items = ItemList.Builder()
        if (s.readings.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle(STATUS_TITLE)
                    .addText(statusMessage(s))
                    .setImage(icons.app, Row.IMAGE_TYPE_ICON)
                    .build()
            )
        } else {
            for (r in s.readings) {
                items.addItem(
                    Row.Builder()
                        .setTitle(r.sensor.longName.ifBlank { r.sensor.label })
                        .addText(valueText(r, s.status))
                        .setImage(icons.forKind(r.sensor.iconKind()), Row.IMAGE_TYPE_ICON)
                        .build()
                )
            }
        }
        return builder.setSingleList(items.build()).build()
    }

    private fun actionStrip(): ActionStrip =
        ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Sensores")
                    // Elegir sensores solo con el auto detenido.
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        screenManager.push(SensorPickerScreen(carContext))
                    })
                    .build()
            )
            .build()

    private fun valueText(r: Reading, status: TorqueStatus): String {
        val text = if (r.unit.isBlank()) r.value else "${r.value} ${r.unit}"
        // Sin dato, decir por qué (el título del ítem no puede cambiar, el texto sí).
        return if (r.value == "—" && status != TorqueStatus.LIVE) "$text · ${shortStatus(status)}" else text
    }

    private fun shortStatus(status: TorqueStatus): String = when (status) {
        TorqueStatus.NOT_INSTALLED -> "sin Torque"
        TorqueStatus.CONNECTING -> "abrí Torque"
        TorqueStatus.NO_ECU -> "sin OBD"
        TorqueStatus.LIVE -> ""
    }

    private fun statusMessage(s: DashboardState.Ready): String = when (s.status) {
        TorqueStatus.NOT_INSTALLED -> "Torque Pro no está instalado en el teléfono"
        TorqueStatus.CONNECTING -> "Abrí Torque Pro en el teléfono"
        TorqueStatus.NO_ECU -> "Torque no está conectado al adaptador OBD"
        TorqueStatus.LIVE ->
            if (s.autoMode) "Sin sensores activos en Torque. Elegí sensores en \"Sensores\""
            else "No hay sensores elegidos"
    }

    private fun contentLimit(): Int {
        val type = if (useList) ConstraintManager.CONTENT_LIMIT_TYPE_LIST
        else ConstraintManager.CONTENT_LIMIT_TYPE_GRID
        val limit = try {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(type)
        } catch (e: Exception) {
            DEFAULT_LIMIT // autos con API de nivel 1
        }
        return limit.coerceIn(1, MAX_ITEMS)
    }

    private companion object {
        const val TITLE = "AutoTorque"
        const val STATUS_TITLE = "Estado"
        const val DEFAULT_LIMIT = 6
        const val MAX_ITEMS = 24
    }
}
