package uy.autotorque.torque

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.prowl.torque.remote.ITorqueService

/**
 * Conexión única (para todo el proceso) al servicio de plugins de Torque Pro.
 *
 * La usan a la vez la pantalla del teléfono y la sesión de Android Auto, por eso
 * lleva un contador de usuarios: se conecta con el primero y se desconecta con el último.
 *
 * Todas las llamadas a Torque son IPC sincrónicas: hacerlas fuera del hilo principal.
 */
object TorqueConnection {

    const val TORQUE_PACKAGE = "org.prowl.torque"
    private const val TORQUE_SERVICE = "org.prowl.torque.remote.TorqueService"
    private const val TAG = "TorqueConnection"

    @Volatile
    private var service: ITorqueService? = null

    /** true si activamos el modo prueba de Torque desde esta app (datos simulados, sin auto). */
    @Volatile
    var testModeActive = false

    // Estado de enlace: solo se toca desde el hilo principal.
    private var users = 0
    private var bound = false
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ITorqueService.Stub.asInterface(binder)
            Log.i(TAG, "Conectado al servicio de Torque")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Torque se cerró o se cayó. Android reconecta solo cuando vuelve.
            service = null
            Log.i(TAG, "Torque se desconectó")
        }

        override fun onBindingDied(name: ComponentName?) {
            // El enlace quedó inutilizable (p. ej. Torque se actualizó): rehacerlo.
            service = null
            main.post {
                unbindInternal()
                if (users > 0) bindInternal()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            service = null
        }
    }

    /** Llamar desde el hilo principal al empezar a usar Torque. */
    fun acquire(context: Context) {
        appContext = context.applicationContext
        users++
        if (!bound) bindInternal()
    }

    /** Llamar desde el hilo principal al dejar de usar Torque. */
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) unbindInternal()
    }

    /** Reintenta el enlace si falló antes (p. ej. Torque se instaló después). Seguro desde cualquier hilo. */
    fun retryBindIfNeeded() {
        main.post { if (users > 0 && !bound) bindInternal() }
    }

    fun isTorqueInstalled(context: Context): Boolean = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(TORQUE_PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(TORQUE_PACKAGE, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    val isServiceConnected: Boolean
        get() = service != null

    /**
     * Ejecuta una llamada contra Torque. Devuelve null si no hay conexión o si la llamada falla,
     * así quien llama nunca tiene que manejar excepciones de IPC.
     */
    fun <T> call(block: (ITorqueService) -> T): T? {
        val s = service ?: return null
        return try {
            block(s)
        } catch (e: DeadObjectException) {
            service = null
            retryBindIfNeeded()
            null
        } catch (e: Exception) {
            // RemoteException, SecurityException o errores dentro de Torque: no tirar la app abajo.
            Log.w(TAG, "Fallo al llamar a Torque", e)
            null
        }
    }

    private fun bindInternal() {
        val ctx = appContext ?: return
        if (!isTorqueInstalled(ctx)) {
            Log.w(TAG, "Torque Pro no está instalado")
            return
        }
        val intent = Intent().setClassName(TORQUE_PACKAGE, TORQUE_SERVICE)
        bound = try {
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para enlazar con Torque", e)
            false
        }
        if (!bound) {
            // bindService devolvió false: igual hay que liberar el intento.
            try {
                ctx.unbindService(connection)
            } catch (e: IllegalArgumentException) { // no estaba enlazado
            }
        }
    }

    private fun unbindInternal() {
        val ctx = appContext ?: return
        if (bound) {
            try {
                ctx.unbindService(connection)
            } catch (e: IllegalArgumentException) { // no estaba enlazado
            }
        }
        bound = false
        service = null
    }
}
