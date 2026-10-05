package uy.autotorque.torque

import java.util.Locale
import kotlin.math.abs

/**
 * Torque entrega los valores por esta API en unidades métricas "crudas".
 * Si en Torque elegiste otras unidades (°F, mph, psi...), acá se convierten.
 * Si no sabemos convertir un par de unidades, se muestra el valor crudo con su
 * unidad cruda: nunca un número con la unidad equivocada.
 */
object Units {

    fun normalize(unit: String): String =
        unit.trim().lowercase(Locale.ROOT).replace("º", "°").replace(" ", "")

    /** Devuelve el valor convertido, o null si no hay conversión conocida. */
    fun convert(value: Float, from: String, to: String): Float? {
        val f = normalize(from)
        val t = normalize(to)
        if (f == t) return value
        return when {
            f == "°c" && t == "°f" -> value * 9f / 5f + 32f
            f == "km/h" && t == "mph" -> value * 0.621371f
            f == "km" && (t == "miles" || t == "mi" || t == "mile") -> value * 0.621371f
            f == "m" && (t == "ft" || t == "feet") -> value * 3.28084f
            f == "kpa" && t == "psi" -> value * 0.145038f
            f == "kpa" && t == "bar" -> value / 100f
            f == "kpa" && t == "inhg" -> value * 0.2953f
            f == "l" && t == "gal" -> value * 0.264172f
            f == "l/h" && t == "gal/h" -> value * 0.264172f
            f == "nm" && (t == "lb-ft" || t == "ft-lb" || t == "lbft") -> value * 0.737562f
            f == "kw" && t == "hp" -> value * 1.34102f
            f == "l/100km" && t.startsWith("mpg") -> {
                if (value <= 0f) null
                else if ("uk" in t) 282.481f / value else 235.215f / value
            }
            else -> null
        }
    }

    /** Formatea con decimales según la magnitud: 2150 · 92,5 · 0,98 */
    fun format(value: Float, unit: String): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val decimals = when {
            normalize(unit) == "rpm" -> 0
            abs(value) >= 100f -> 0
            abs(value) >= 10f -> 1
            else -> 2
        }
        return String.format(Locale.getDefault(), "%.${decimals}f", value)
    }
}
