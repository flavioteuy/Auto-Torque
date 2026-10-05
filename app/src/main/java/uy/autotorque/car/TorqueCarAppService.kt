package uy.autotorque.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uy.autotorque.torque.TorqueConnection

/** Punto de entrada que Android Auto busca en el manifest. */
class TorqueCarAppService : CarAppService() {

    // App de uso personal instalada fuera de Play Store: se acepta cualquier host.
    // Si algún día la publicás, reemplazar por una lista de hosts permitidos.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = TorqueSession()
}

class TorqueSession : Session() {

    private var holdsTorque = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (holdsTorque) {
                    TorqueConnection.release()
                    holdsTorque = false
                }
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        if (!holdsTorque) {
            TorqueConnection.acquire(carContext)
            holdsTorque = true
        }
        return DashboardScreen(carContext)
    }
}
