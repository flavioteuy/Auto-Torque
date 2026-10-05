# AutoTorque

App para Android que lee los datos de **Torque Pro** (plugin oficial de Torque) y los muestra en la pantalla del auto con **Android Auto**.

- Tablero en mosaicos o en lista, con ícono por tipo de sensor (temperatura, batería, combustible, general).
- Elegís qué sensores ver y en qué orden, desde el teléfono o desde el auto (detenido).
- Sin selección, muestra automáticamente los sensores activos en Torque.
- Respeta las unidades que elegiste en Torque (°C/°F, km/h/mph, kPa/psi/bar, etc.).
- Vista previa en vivo en el teléfono y "Modo prueba" de Torque para probar sin el auto.

## Requisitos

- **Torque Pro** (`org.prowl.torque`). Torque Lite no admite plugins.
- Adaptador OBD2 (Bluetooth/Wi-Fi) ya funcionando con Torque.
- Android 8.0 o superior en el teléfono.
- Una PC con Windows y cable USB (o descargar el APK directo en el teléfono).

## 1. Instalar

**Opción A — desde Windows por USB (recomendada):**
1. En el teléfono: *Ajustes → Acerca del teléfono* → tocá 7 veces *Número de compilación*. Después, en *Opciones de desarrollador*, activá **Depuración USB**.
2. Conectá el teléfono a la PC con un cable de datos.
3. Ejecutá **`instalar-AutoTorque.bat`**. Descarga solo lo que falte (adb y la última versión del APK), instala y abre la app.

**Opción B — desde el teléfono:** descargá `AutoTorque.apk` de [Releases](https://github.com/flavioteuy/auto-torque/releases/latest) y abrilo (Android pide permitir "instalar apps desconocidas").

**Opción C — Android Studio:** abrí esta carpeta (*File → Open*) y tocá **Run ▶**.

Cada cambio que se sube a `main` se compila solo en GitHub Actions y publica una versión nueva en Releases. Todas las versiones van firmadas con la misma clave (`app/autotorque.jks`), así que se instalan encima de la anterior sin perder la configuración.

## 2. Habilitar la app en Android Auto (una sola vez)

Android Auto solo muestra apps instaladas desde Play Store, salvo que habilites "Fuentes desconocidas":

1. En el teléfono: *Ajustes → Dispositivos conectados → Preferencias de conexión → Android Auto* (o buscá "Android Auto" en Ajustes).
2. Bajá hasta **Versión** y tocalo unas 10 veces, hasta que aparezca el aviso de modo desarrollador.
3. Menú **⋮ → Configuración para desarrolladores** → marcá **Fuentes desconocidas**.
4. Si no ves el ícono en el auto: en Android Auto, *Personalizar launcher* y activá **AutoTorque**.

## 3. Uso

1. Abrí **Torque Pro** y conectalo al adaptador OBD (o activá su conexión automática al arrancar).
2. Abrí **AutoTorque** en el teléfono, marcá los sensores que querés ver (el orden en que los marcás es el orden en pantalla).
3. En el auto, abrí **AutoTorque** desde el launcher de Android Auto.

Con el auto en marcha, Android Auto suele limitar la pantalla a unos 6 elementos: poné primero los más importantes.

## Probar sin el auto

- **Modo prueba**: en la app del teléfono, botón *Modo prueba* → Torque simula lecturas de algunos sensores.
- **Emulador de Android Auto (DHU)**:
  1. En Android Studio: *SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator*.
  2. En el teléfono, menú de desarrollador de Android Auto → **Iniciar servidor de unidad principal**.
  3. En la computadora: `adb forward tcp:5277 tcp:5277`
  4. Ejecutá `desktop-head-unit` (está en `<SDK>/extras/google/auto/`).

## Solución de problemas

| Problema | Qué hacer |
|---|---|
| La app no aparece en Android Auto | Revisá "Fuentes desconocidas" (paso 2). Si sigue sin aparecer, en `AndroidManifest.xml` cambiá `androidx.car.app.category.IOT` por `androidx.car.app.category.POI` y reinstalá. |
| "Abrí Torque Pro en el teléfono" | Torque está cerrado. Abrilo; la app se reconecta sola. |
| "sin OBD" junto a los valores | Torque está abierto pero no conectado al adaptador. |
| Los valores no se mueven | Bajá el intervalo en *Actualizar cada*. Torque lee más lento cuantos más sensores pedís. |
| Aparece un mensaje de error y la app se cierra en el auto | Android Auto limita la cantidad de pantallas por tarea. Volvé a abrir la app desde el launcher. |

## Cómo está armado

```
app/src/main/
├── aidl/org/prowl/torque/remote/ITorqueService.aidl   Interfaz de plugins de Torque (no reordenar)
└── java/uy/autotorque/
    ├── torque/TorqueConnection.kt   Conexión al servicio de Torque (reintentos, errores)
    ├── torque/Sensor.kt, Units.kt   Modelo de sensor, íconos y conversión de unidades
    ├── data/AppSettings.kt          Preferencias (sensores elegidos, vista, intervalo)
    ├── data/DashboardFeed.kt        Lee Torque en segundo plano y emite el estado del tablero
    ├── car/                         Android Auto: servicio, sesión, tablero y selector de sensores
    └── phone/MainActivity.kt        Configuración y vista previa en el teléfono
```

Detalle importante de Android Auto: cada actualización de pantalla mantiene el mismo título y los mismos nombres de sensores, y solo cambia el texto del valor. Así Android Auto la toma como "refresco" y no como una pantalla nueva (hay un cupo de 5 por tarea).

La clave de firma está en el repo para que GitHub pueda compilar sin configurar nada. Como el repo es público, si querés más seguridad pasala a *Settings → Secrets* y sacala del repo.

App para uso personal. Para publicarla en Play Store habría que cambiar el validador de hosts en `TorqueCarAppService.kt` y pasar la revisión de Google para apps de autos.
