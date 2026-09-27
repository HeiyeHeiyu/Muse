plugins {
    alias(libs.plugins.android.application)
}

// v2.2.1 虚拟屏:shell uid 独立进程服务端。
//
// 产物是 APK,但从不安装 —— 改名 jar 后由主应用写到 /data/local/tmp,
// 用 `CLASSPATH=<jar> app_process / io.zer0.muse.vdserver.Main <hostPkg>` 拉起。
//
// 全 Java + 零第三方依赖:保证 classes.dex 单文件(带 kotlin-stdlib 会变多 dex,
// app_process 的 CLASSPATH 只能可靠加载第一段 dex)。
android {
    namespace = "io.zer0.muse.vdserver"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.zer0.muse.vdserver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // app_process 的 CLASSPATH 只能可靠加载 classes.dex 一段,强制单 dex
        multiDexEnabled = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":virtual-display-protocol"))
}
