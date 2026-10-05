package uy.autotorque.car

import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import uy.autotorque.R
import uy.autotorque.torque.IconKind

/** Crea los íconos una sola vez por pantalla. */
class CarIcons(context: CarContext) {

    private fun icon(context: CarContext, res: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(context, res))
            .setTint(CarColor.DEFAULT)
            .build()

    private val gauge = icon(context, R.drawable.ic_gauge)
    private val temperature = icon(context, R.drawable.ic_thermo)
    private val battery = icon(context, R.drawable.ic_battery)
    private val fuel = icon(context, R.drawable.ic_fuel)

    val app: CarIcon = gauge

    fun forKind(kind: IconKind): CarIcon = when (kind) {
        IconKind.GAUGE -> gauge
        IconKind.TEMPERATURE -> temperature
        IconKind.BATTERY -> battery
        IconKind.FUEL -> fuel
    }
}
