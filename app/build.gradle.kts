import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.parcelize")
    id ("kotlin-kapt")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
    id("androidx.navigation.safeargs.kotlin") // для safeargs в навигацию
}

android {
    namespace = "com.example.muzpleer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.muzpleer_4tabs"
        minSdk = 24
        targetSdk = 35
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Измените с VERSION_11
        targetCompatibility = JavaVersion.VERSION_17 // Измените с VERSION_11
    }
    kotlinOptions {
        jvmTarget = "17" // Измените с "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    //preference
    implementation ("androidx.preference:preference-ktx:1.2.1")

    //exoplayer
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")
    // Koin
    implementation ("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation ("io.insert-koin:koin-androidx-navigation:3.5.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    //Splash Screen
    implementation ("androidx.core:core-splashscreen:1.2.0") // или последнюю версию

    //room
    implementation ("androidx.room:room-runtime:2.6.1") // Актуальная версия на момент 2024
    kapt ("androidx.room:room-compiler:2.6.1" ) // Используйте kapt без скобок
    implementation ("androidx.room:room-ktx:2.6.1")  // Корректный синтаксис


    //glide
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    //picasso
    implementation ("com.squareup.picasso:picasso:2.8")

    //PhotoView
    implementation ("com.github.chrisbanes:PhotoView:2.3.0")

    //CircleImageView http://developer.alexanderklimov.ru/android/library/circleimageview.php
    implementation ("de.hdodenhof:circleimageview:3.1.0")

    //gson
    implementation ("com.google.code.gson:gson:2.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}