/**
 * build.gradle.kts 是模块级的构建配置文件（当前位于 app 目录下）。
 * 
 * 它的核心作用包括：
 * 1. 插件管理：引入 Android 构建插件和 Kotlin 编译支持。
 * 2. 编译配置：设定编译所需的 SDK 版本 (compileSdk)、应用唯一识别符 (applicationId) 以及运行环境 (minSdk/targetSdk)。
 * 3. 构建变体管理：配置 Release 和 Debug 版本的构建行为（如签名配置、混淆规则等）。
 * 4. 依赖管理：通过 dependencies 代码块管理项目引用的第三方库和框架。
 * 
 * Android 项目通常通过 Gradle 构建系统将源代码、资源文件以及外部库打包成最终的 APK 安装包。
 */

// 插件配置：引入 Android 应用插件和 Kotlin Android 支持
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 应用在代码中的包名空间
    namespace = "com.linghuye.memowidget"
    // 编译时使用的 Android SDK 版本
    compileSdk = 34

    defaultConfig {
        // 应用的唯一标识 ID（通常在 Google Play 上唯一）
        applicationId = "com.linghuye.memowidget"
        // 应用运行所需的最低 Android SDK 版本
        minSdk = 26
        // 针对的目标 SDK 版本，系统会在此版本下启用最新的优化特性
        targetSdk = 34
        // 应用的版本代码，用于商店升级判断
        versionCode = 1
        // 应用显示给用户的版本名称
        versionName = "1.0"
    }

    // 构建类型配置（如 Release 生产版、Debug 调试版）
    buildTypes {
        release {
            // 是否启用代码压缩、混淆（减小 APK 体积）
            isMinifyEnabled = false
            // 目前 Release 暂用 Debug 签名，方便直接测试
            signingConfig = signingConfigs.getByName("debug")
            // 配置 Proguard 混淆规则文件
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Java 版本兼容性设置（此处设定为 Java 17）
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Kotlin 编译选项，设定 JVM 目标版本
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 依赖库配置：定义项目中引用的第三方库
dependencies {
    // Kotlin 核心扩展库
    implementation("androidx.core:core-ktx:1.12.0")
    // Android 基础 AppCompat 支持库（用于 UI 兼容）
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Google Material Design UI 组件库（用于 TabLayout 等）
    implementation("com.google.android.material:material:1.11.0")
}
