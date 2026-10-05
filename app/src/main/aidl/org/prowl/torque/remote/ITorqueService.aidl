package org.prowl.torque.remote;

// Interfaz pública de plugins de Torque Pro (publicada por su autor, Ian Hawkins).
// IMPORTANTE: no reordenar, agregar ni quitar métodos. AIDL numera las llamadas
// según el orden en que aparecen; si cambia el orden, se llaman métodos equivocados.

interface ITorqueService {

   int getVersion();

   float getValueForPid(long pid, boolean triggersDataRefresh);

   String getDescriptionForPid(long pid);

   String getShortNameForPid(long pid);

   String getUnitForPid(long pid);

   float getMinValueForPid(long pid);

   float getMaxValueForPid(long pid);

   long[] getListOfActivePids();

   long[] getListOfECUSupportedPids();

   long[] getListOfAllPids();

   boolean hasFullPermissions();

   String[] sendCommandGetResponse(String header, String command);

   /** Dada una unidad "cruda" (p. ej. °C), devuelve la unidad preferida por el usuario en Torque. */
   String getPreferredUnit(String unit);

   boolean setPIDData(String name, String shortName, String unit, float max, float min, float value);

   /** true si Torque ya está leyendo datos de la ECU. */
   boolean isConnectedToECU();

   /** Modo de prueba de Torque: simula lecturas de algunos sensores. */
   boolean setDebugTestMode(boolean activateTestMode);

   String[] getVehicleProfileInformation();

   int storeInProfile(String key, String value, boolean saveToFileNow);

   String retrieveProfileData(String key);

   int getDataErrorCount();

   double getPIDReadSpeed();

   int getConfiguredSpeed();

   boolean isFileLoggingEnabled();

   boolean isWebLoggingEnabled();

   int getNumberOfLoggedItems();

   long getUpdateTimeForPID(long pid);

   float getScaleForPid(long pid);

   String translate(String originalText);

   boolean sendPIDData(String pluginName, in String[] name, in String[] shortName, in String[] modeAndPID, in String[] equation, in float[] minValue, in float[] maxValue, in String[] units, in String[] header);

   boolean setPIDInformation(String name, String shortName, String unit, float max, float min, float value, String stringValue);

   /** IDs de todos los sensores conocidos por Torque (activos o no). Costoso: no llamar seguido. */
   String[] listAllPIDs();

   /** IDs de los sensores activos en este momento. */
   String[] listActivePIDs();

   String[] listECUSupportedPIDs();

   /** Valores actuales (en unidad cruda) de los IDs pedidos. Es la llamada para el refresco frecuente. */
   float[] getPIDValues(in String[] pidsToRetrieve);

   /** Un CSV por ID: "<nombreLargo>,<nombreCorto>,<unidad>,<máx>,<mín>,<escala>" */
   String[] getPIDInformation(in String[] pidIDs);

   long[] getPIDUpdateTime(in String[] pidIDs);

   float[] getValueForPids(in long[] pids);

   boolean sendPIDDataPrivate(String pluginName, in String[] name, in String[] shortName, in String[] modeAndPID, in String[] equation, in float[] minValue, in float[] maxValue, in String[] units, in String[] header);

   int requestExclusiveLock(String pluginName);

   boolean releaseExclusiveLock(String pluginName, boolean torqueMustReInitializeTheAdapter);

   boolean sendPIDDataV2(String pluginName, in String[] name, in String[] shortName, in String[] modeAndPID, in String[] equation, in float[] minValue, in float[] maxValue, in String[] units, in String[] header, in String[] startDiagnostic, in String[] stopDiagnostic);

   boolean sendPIDDataPrivateV2(String pluginName, in String[] name, in String[] shortName, in String[] modeAndPID, in String[] equation, in float[] minValue, in float[] maxValue, in String[] units, in String[] header,  in String[] startDiagnostic, in String[] stopDiagnostic);

   String[] getPIDRawResponse(String OBDCommand);

   int getProtocolNumber();

   String getProtocolName();
}
