import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 릴리스 서명 정보는 프로젝트 밖의 파일에서 읽음 (비밀번호를 저장소에 넣지 않기 위해).
// 기본 위치: <사용자 폴더>/keystore/GemTower.properties
// 다른 위치를 쓰려면 -Pgemtower.signing=<경로> 또는 환경변수 GEMTOWER_SIGNING 으로 지정.
val signingPropsFile: File = file(
    providers.gradleProperty("gemtower.signing").orNull
        ?: System.getenv("GEMTOWER_SIGNING")
        ?: "${System.getProperty("user.home")}/keystore/GemTower.properties"
)
val signingProps = Properties().apply {
    if (signingPropsFile.isFile) signingPropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !signingProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.tambusrin.gemtower"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tambusrin.gemtower"
        minSdk = 30
        targetSdk = 37
        versionCode = 9
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            // 서명 파일이 없는 PC에서는 서명 없이 빌드됨 (빌드 자체는 실패하지 않음)
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("GemTower: 서명 정보 파일이 없어 release 빌드가 서명되지 않습니다: $signingPropsFile")
            }
            // 템플릿 기본값의 packageScope(androidx/kotlin만 부분 최적화)는 실행 시
            // IllegalAccessError로 앱이 시작되지 않아서 제거함. 앱 전체를 함께 최적화.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}