import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
        }
    }
}

val keystoreProps = Properties()
val propsFiles = listOf("keystore.properties", "android_keystore.props", "../keystore.properties", "../android_keystore.props")
for (pf in propsFiles) {
    val f = file(pf)
    if (f.exists()) {
        FileInputStream(f).use { keystoreProps.load(it) }
        break
    }
}

fun getSigningValue(envKeys: List<String>, propKeys: List<String>): String? {
    for (k in envKeys) {
        val v = System.getenv(k)?.trim()
        if (!v.isNullOrEmpty()) return v
    }
    for (k in propKeys) {
        val v = keystoreProps.getProperty(k)?.trim()
        if (!v.isNullOrEmpty()) return v
    }
    return null
}

val resolvedKeystorePath = getSigningValue(
    listOf("CM_KEYSTORE_PATH", "STORE_FILE", "KEYSTORE_FILE", "ANDROID_KEYSTORE_PATH"),
    listOf("storeFile", "CM_KEYSTORE_PATH", "STORE_FILE")
)
val resolvedKeystorePassword = getSigningValue(
    listOf("CM_KEYSTORE_PASSWORD", "STORE_PASSWORD", "KEYSTORE_PASSWORD"),
    listOf("storePassword", "CM_KEYSTORE_PASSWORD", "STORE_PASSWORD")
)
val resolvedKeyAlias = getSigningValue(
    listOf("CM_KEY_ALIAS", "KEY_ALIAS", "ALIAS"),
    listOf("keyAlias", "CM_KEY_ALIAS", "KEY_ALIAS")
)
val resolvedKeyPassword = getSigningValue(
    listOf("CM_KEY_PASSWORD", "KEY_PASSWORD"),
    listOf("keyPassword", "CM_KEY_PASSWORD", "KEY_PASSWORD")
) ?: resolvedKeystorePassword

val ciBuildNumber: Int = System.getenv("BUILD_NUM")?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100)
    ?: 101
val appVer: String = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0"

android {
    namespace = "cz.mares.bombman"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.bombman"
        minSdk = 24
        targetSdk = 36
        versionCode = ciBuildNumber
        versionName = "$appVer.$ciBuildNumber"
    }

    signingConfigs {
        create("releaseConfig") {
            val ksFile = resolvedKeystorePath?.let { file(it) }
            if (ksFile != null && ksFile.exists() && !resolvedKeystorePassword.isNullOrEmpty() && !resolvedKeyAlias.isNullOrEmpty()) {
                storeFile = ksFile
                storePassword = resolvedKeystorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val relCfg = signingConfigs.getByName("releaseConfig")
            if (relCfg.storeFile != null && relCfg.storeFile!!.exists()) {
                signingConfig = relCfg
            }
        }
        getByName("debug") {
            val relCfg = signingConfigs.getByName("releaseConfig")
            if (relCfg.storeFile != null && relCfg.storeFile!!.exists()) {
                signingConfig = relCfg
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
