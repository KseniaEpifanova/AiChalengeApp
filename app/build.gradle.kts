plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.aichalengeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aichalengeapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val deepSeekApiKey = (project.findProperty("DEEPSEEK_API_KEY") as String?) ?: ""
        val deepSeekBaseUrl = (project.findProperty("DEEPSEEK_BASE_URL") as String?) ?: "https://api.deepseek.com/v1/"
        val ollamaBaseUrl = (project.findProperty("OLLAMA_BASE_URL") as String?) ?: "http://10.0.2.2:11434"
        val mcpBaseUrl = (project.findProperty("MCP_BASE_URL") as String?) ?: "https://prompts.chat/api/mcp"
        val mcpCurrencyBaseUrl = (project.findProperty("MCP_CURRENCY_BASE_URL") as String?) ?: mcpBaseUrl
        val mcpPipelineBaseUrl = (project.findProperty("MCP_PIPELINE_BASE_URL") as String?) ?: mcpBaseUrl

        buildConfigField("String", "DEEPSEEK_API_KEY", deepSeekApiKey.asBuildConfigString())
        buildConfigField("String", "DEEPSEEK_BASE_URL", deepSeekBaseUrl.asBuildConfigString())
        buildConfigField("String", "OLLAMA_BASE_URL", ollamaBaseUrl.asBuildConfigString())
        buildConfigField("String", "MCP_BASE_URL", mcpBaseUrl.asBuildConfigString())
        buildConfigField("String", "MCP_CURRENCY_BASE_URL", mcpCurrencyBaseUrl.asBuildConfigString())
        buildConfigField("String", "MCP_PIPELINE_BASE_URL", mcpPipelineBaseUrl.asBuildConfigString())
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
hilt {
    enableAggregatingTask = false
}
dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.mcp.kotlin.sdk)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
