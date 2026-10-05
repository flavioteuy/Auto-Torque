package uy.autotorque.torque

/**
 * Un sensor de Torque.
 *
 * @param id     identificador opaco que devuelve Torque (listAllPIDs / listActivePIDs)
 * @param unit   unidad "cruda" en la que Torque entrega el valor (métrica)
 */
data class Sensor(
    val id: String,
    val longName: String,
    val shortName: String,
    val unit: String,
) {
    /** Nombre corto si existe; si no, el largo. */
    val label: String
        get() = shortName.ifBlank { longName }

    companion object {
        /**
         * Interpreta una línea de getPIDInformation:
         * "<nombreLargo>,<nombreCorto>,<unidad>,<máx>,<mín>,<escala>".
         * Se lee desde el final porque el nombre largo a veces trae comas.
         */
        fun fromTorqueCsv(id: String, csv: String?): Sensor {
            if (csv.isNullOrBlank()) return Sensor(id, id, id, "")
            val parts = csv.split(",")
            return if (parts.size >= 6) {
                val n = parts.size
                Sensor(
                    id = id,
                    longName = parts.subList(0, n - 5).joinToString(",").trim(),
                    shortName = parts[n - 5].trim(),
                    unit = parts[n - 4].trim(),
                )
            } else {
                Sensor(id, parts.getOrElse(0) { id }.trim(), parts.getOrElse(1) { "" }.trim(), parts.getOrElse(2) { "" }.trim())
            }
        }
    }
}

/** Tipo de ícono a mostrar en el auto, deducido de la unidad y el nombre. */
enum class IconKind { GAUGE, TEMPERATURE, BATTERY, FUEL }

fun Sensor.iconKind(): IconKind {
    val u = Units.normalize(unit)
    val n = (longName + " " + shortName).lowercase()
    return when {
        u == "°c" || u == "°f" -> IconKind.TEMPERATURE
        u == "v" -> IconKind.BATTERY
        u.startsWith("l/") || u.startsWith("mpg") || u == "l" || u == "gal" ||
            "fuel" in n || "combustible" in n -> IconKind.FUEL
        else -> IconKind.GAUGE
    }
}
