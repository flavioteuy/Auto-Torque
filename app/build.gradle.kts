import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clave de firma propia: todos los builds (GitHub o Android Studio) salen con la misma firma,
// así cada versión nueva se instala encima de la anterior sin perder la configuración.
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// En GitHub Actions cada build tiene un número mayor que el anterior.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "uy.autotorque"
    compileSdk = 35

    defaultConfig {
        applicationId = "uy.autotorque"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    buildFeatures {
        // Necesario en AGP 8+ para compilar la interfaz de plugins de Torque (ITorqueService.aidl)
        aidl = true
    }

    signingConfigs {
        create("autotorque") {
            storeFile = file(keystoreProps.getProperty("storeFile", "autotorque.jks"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("autotorque")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("autotorque")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Biblioteca oficial de apps para Android Auto
    implementation("androidx.car.app:app:1.7.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
