import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// google-services.json 은 저장소에 없다(커밋하지 않기로 했다 — 기계마다 Firebase 콘솔에서
// 받는다). 없는 기계에서도 빌드는 되고, 앱은 알림 없이 뜬다(FirebasePushMessaging.create).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("google-services.json 이 없다 — 알림 없는 빌드다")
}

// 릴리스 서명(업로드 키). android/key.properties 와 키스토어 파일은 저장소에 없다(.gitignore) —
// 서명 키가 새면 남이 이 앱의 업데이트를 만들 수 있다. 형식은 storeFile·storePassword·keyAlias·keyPassword.
//
// **없으면 릴리스 빌드를 실패시킨다(2026-09-30 리뷰).** 예전에는 debug 키로 서명했다 — Play 스토어가
// 받지 않고, 그 상태로 한 번이라도 사이드로드하면 나중에 키를 바꿀 때 업데이트가 설치되지 않아
// 사용자가 앱을 지웠다 다시 깔아야 한다. 디버그 빌드·`flutter run` 은 그대로 된다.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseKey = keystorePropertiesFile.exists()

gradle.taskGraph.whenReady {
    val releasePackaging = allTasks.any { task ->
        task.project == project &&
            (task.name.startsWith("assemble") || task.name.startsWith("bundle") ||
                task.name.startsWith("package")) &&
            task.name.contains("Release")
    }
    if (releasePackaging && !hasReleaseKey) {
        throw GradleException(
            "릴리스 서명 키가 없다: android/key.properties 를 만들어라 " +
                "(storeFile·storePassword·keyAlias·keyPassword). debug 키로 서명하지 않는다."
        )
    }
}

android {
    namespace = "com.njwenglish.academy_app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.njwenglish.academy_app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 키가 없으면 비워 둔다 — 위의 taskGraph 검사가 릴리스 패키징을 막는다
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
