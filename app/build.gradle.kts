plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    id("com.google.gms.google-services")
}

android {
    namespace = "com.luixard.studios"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luixard.studios"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- LIBRERÍAS DE STUDIOS (SPRINT 1) ---
    val room_version = "2.6.1"
    val lifecycle_version = "2.7.0"

    // Base de datos local (SQLite)
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // ViewModel y LiveData (Arquitectura MVVM)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")

    // Corrutinas (Para ejecutar tareas de BD en segundo plano sin trabar la pantalla)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Librería necesaria para usar 'by viewModels()' en los Fragmentos
    implementation("androidx.fragment:fragment-ktx:1.6.2")


    // --- FIREBASE Y AUTENTICACIÓN (SPRINT 4) ---

    // 1. Importa la plataforma (BoM) de Firebase (Usa la versión más reciente que tenías: 34.11.0)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))

    // 2. Librerías de Firebase (Fíjate que usan comillas dobles y paréntesis)
    // Cuando usas el BoM arriba, ya no necesitas poner la versión en estas líneas de abajo
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")      // Para iniciar sesión
    implementation("com.google.firebase:firebase-firestore") // Para respaldar la base de datos

    // 3. Para el login con el botón de Google
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    //Para el codigo de 5 digitos
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.google.firebase:firebase-firestore")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("com.google.android.gms:play-services-auth:21.2.0")

}