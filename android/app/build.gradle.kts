plugins {
    id("com.android.application")
}

android {
    namespace = "org.phonebridge"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.phonebridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
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
    implementation("androidx.appcompat:appcompat:1.7.0")
}

configurations.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:1.8.22",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22"
    )
}
