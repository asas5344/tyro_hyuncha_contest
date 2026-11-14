plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    useLibrary("android.car", false)
    namespace = "com.team.hyuncha"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.team.hyuncha"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 공모전 제출용: 따로 만든 서명 키 없이도 설치되도록 디버그 키로 서명함.
            // 앱 마켓에 올릴 때는 전용 서명 키로 바꿔야 함.
            signingConfig = signingConfigs.getByName("debug")
            // 코드 축소(R8 최적화)는 끔: 일부 패키지만 최적화하던 설정에서는 앱이 켜지자마자
            // IllegalAccessError로 죽었음 (2026-11-14 Pleos 에뮬레이터 확인).
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Vehicle이 런타임에 가져오는 ErrorCode를 컴파일에서도 사용한다.
    implementation("ai.pleos.playground:vehicle-data:1.1.11") { isTransitive = false }
    implementation("ai.pleos.playground:SpeechToText:3.3.1")   // 음성 명령 (차 안 엔진, 앱 등록 불필요)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
