package uy.autotorque.phone

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.autotorque.R
import uy.autotorque.data.AppSettings
import uy.autotorque.data.DashboardFeed
import uy.autotorque.data.DashboardState
import uy.autotorque.data.TorqueRepository
import uy.autotorque.data.TorqueStatus
import uy.autotorque.torque.Sensor
import uy.autotorque.torque.TorqueConnection

/**
 * Pantalla del teléfono: elegir sensores, opciones y una vista previa en vivo
 * de lo que se va a ver en Android Auto.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var previewText: TextView
    private lateinit var testModeButton: Button

    private var allSensors: List<Sensor> = emptyList()
    private val selected = LinkedHashMap<String, Sensor>()
    private val adapter = SensorAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        previewText = findViewById(R.id.previewText)
        testModeButton = findViewById(R.id.testModeButton)

        val listLayoutSwitch = findViewById<SwitchCompat>(R.id.listLayoutSwitch)
        listLayoutSwitch.isChecked = AppSettings.useListLayout(this)
        listLayoutSwitch.setOnCheckedChangeListener { _, checked ->
            AppSettings.setUseListLayout(this, checked)
            Toast.makeText(this, "Se aplica la próxima vez que abras la app en el auto", Toast.LENGTH_SHORT).show()
        }

        setupRefreshSpinner(findViewById(R.id.refreshSpinner))

        findViewById<Button>(R.id.autoButton).setOnClickListener {
            selected.clear()
            saveSelection()
            Toast.makeText(this, "Modo automático: se muestran los sensores activos en Torque", Toast.LENGTH_SHORT).show()
        }

        testModeButton.setOnClickListener { toggleTestMode() }
        updateTestModeButton()

        val list = findViewById<ListView>(R.id.sensorList)
        list.adapter = adapter
        list.emptyView = findViewById<TextView>(R.id.emptyText)
        list.setOnItemClickListener { _, _, position, _ ->
            val s = allSensors[position]
            if (selected.remove(s.id) == null) selected[s.id] = s
            saveSelection()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { loadSensorListWhenReady() }
                DashboardFeed.states(this@MainActivity, MAX_PREVIEW).collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TorqueConnection.acquire(this)
        // Puede haber cambiado desde el auto.
        selected.clear()
        AppSettings.selectedSensors(this).forEach { selected[it.id] = it }
        adapter.notifyDataSetChanged()
    }

    override fun onStop() {
        TorqueConnection.release()
        super.onStop()
    }

    private fun saveSelection() {
        AppSettings.setSelectedSensors(this, selected.values.toList())
        adapter.notifyDataSetChanged()
    }

    private suspend fun loadSensorListWhenReady() {
        while (allSensors.isEmpty()) {
            val list = withContext(Dispatchers.IO) { TorqueRepository.allSensors() }
            if (!list.isNullOrEmpty()) {
                // Los elegidos arriba (en su orden), el resto alfabético.
                val chosen = selected.keys.mapNotNull { id -> list.firstOrNull { it.id == id } }
                allSensors = chosen + list.filter { it.id !in selected }
                adapter.notifyDataSetChanged()
                return
            }
            delay(2000)
        }
    }

    private fun render(state: DashboardState) {
        if (state !is DashboardState.Ready) {
            statusText.text = "Conectando con Torque Pro…"
            previewText.text = ""
            return
        }
        statusText.text = when (state.status) {
            TorqueStatus.NOT_INSTALLED -> "Torque Pro no está instalado (la versión Lite no admite plugins)"
            TorqueStatus.CONNECTING -> "Esperando a Torque Pro… abrilo"
            TorqueStatus.NO_ECU -> "Torque abierto, pero sin conexión al adaptador OBD"
            TorqueStatus.LIVE ->
                if (TorqueConnection.testModeActive) "Torque en modo prueba (datos simulados)"
                else "Recibiendo datos del auto ✓"
        }
        val mode = if (state.autoMode) "automático" else "elegidos"
        previewText.text = if (state.readings.isEmpty()) {
            "Vista previa ($mode): sin sensores todavía"
        } else {
            "Vista previa ($mode):\n" + state.readings.joinToString("\n") { r ->
                val unit = if (r.unit.isBlank()) "" else " ${r.unit}"
                "${r.sensor.label.take(18).padEnd(18)} ${r.value}$unit"
            }
        }
    }

    private fun setupRefreshSpinner(spinner: Spinner) {
        val options = AppSettings.REFRESH_OPTIONS_MS
        val labels = options.map { ms -> if (ms < 1000) "${ms} ms" else "${ms / 1000} s" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(options.indexOf(AppSettings.refreshMs(this)).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                AppSettings.setRefreshMs(this@MainActivity, options[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun toggleTestMode() {
        val wanted = !TorqueConnection.testModeActive
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TorqueConnection.call { it.setDebugTestMode(wanted) }
            }
            if (result == null) {
                Toast.makeText(this@MainActivity, "Torque Pro no está conectado: abrilo primero", Toast.LENGTH_LONG).show()
            } else {
                TorqueConnection.testModeActive = wanted
                updateTestModeButton()
            }
        }
    }

    private fun updateTestModeButton() {
        testModeButton.text = if (TorqueConnection.testModeActive) "Salir de prueba" else "Modo prueba"
    }

    private inner class SensorAdapter : BaseAdapter() {
        override fun getCount(): Int = allSensors.size
        override fun getItem(position: Int): Any = allSensors[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView ?: layoutInflater.inflate(
                android.R.layout.simple_list_item_multiple_choice, parent, false
            )) as CheckedTextView
            val s = allSensors[position]
            val pos = selected.keys.indexOf(s.id)
            view.text = buildString {
                append(s.longName.ifBlank { s.label })
                if (s.unit.isNotBlank()) append(" (").append(s.unit).append(")")
                if (pos >= 0) append("   #").append(pos + 1)
            }
            view.isChecked = pos >= 0
            return view
        }
    }

    private companion object {
        const val MAX_PREVIEW = 24
    }
}
