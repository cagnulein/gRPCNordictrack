import com.google.protobuf.gradle.*

plugins {
    alias(libs.plugins.android.application)
    id("com.google.protobuf") version "0.9.4"  // Plugin per protobuf
}

android {
    namespace = "org.cagnulein.grpctreadmill"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.cagnulein.grpctreadmill"
        minSdk = 26
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Fix for duplicate META-INF files
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }

    // Configurazione per i file proto
    sourceSets {
        getByName("main") {
            proto {
                srcDir("src/main/proto")  // Directory dove mettere i file .proto
            }
        }
    }
}

// Configurazione protobuf
protobuf {
    protoc {
        // Versione del compilatore protoc
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            // Plugin per generare codice gRPC
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc") {
                    // Genera codice gRPC per tutti i servizi
                    option("lite")  // Usa versione lite ottimizzata per Android
                }
            }
            task.builtins {
                id("java") {
                    option("lite")  // Usa protobuf-lite per Android
                }
            }
        }
    }
}

dependencies {
    // gRPC dependencies (consistent versions, only OkHttp for Android)
    implementation("io.grpc:grpc-okhttp:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
    }
    implementation("io.grpc:grpc-protobuf-lite:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("io.grpc:grpc-stub:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    
    // gRPC server for testing (only for debug builds)
    debugImplementation("io.grpc:grpc-netty:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    
    // Protocol Buffers (consistent version) - only lite version
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    
    // Annotation support
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Android dependencies
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Test dependencies
    testImplementation(libs.junit)
    testImplementation("io.grpc:grpc-testing:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    testImplementation("io.grpc:grpc-inprocess:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    
    // Android test dependencies
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
