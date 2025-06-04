import com.google.protobuf.gradle.*

plugins {
    alias(libs.plugins.android.application)
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "org.cagnulein.grpcNordictrack"
    compileSdk = 35  // Required by AndroidX libraries

    defaultConfig {
        applicationId = "org.cagnulein.grpcNordictrack"
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

    // Enhanced packaging to handle all conflicts
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/MANIFEST.MF"
            )
            // Pick first for duplicate classes
            pickFirsts += setOf(
                "**/libprotobuf-lite.so",
                "**/libprotoc.so"
            )
        }
        
        // Force single dex file strategy for release
        dex {
            useLegacyPackaging = false
        }
    }

    sourceSets {
        getByName("main") {
            proto {
                srcDir("src/main/proto")
            }
        }
    }
}

// Aggressive dependency resolution
configurations.all {
    resolutionStrategy {
        // Force specific versions
        force("com.google.protobuf:protobuf-javalite:3.25.3")
        
        // Exclude all non-lite protobuf variants
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
        exclude(group = "com.google.protobuf", module = "protobuf-kotlin")
        exclude(group = "com.google.protobuf", module = "protobuf-kotlin-lite")
        
        // Exclude problematic dependencies that might bring in full protobuf
        exclude(group = "com.google.api.grpc")
        exclude(group = "io.grpc", module = "grpc-protobuf")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc") {
                    option("lite")
                }
            }
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // Core gRPC - minimal set
    implementation("io.grpc:grpc-okhttp:1.63.0") {
        exclude(group = "com.google.protobuf")
        exclude(group = "io.grpc", module = "grpc-protobuf")
    }
    implementation("io.grpc:grpc-protobuf-lite:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
    }
    implementation("io.grpc:grpc-stub:1.63.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    
    // Only lite protobuf
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    
    // Minimal annotations
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Android dependencies with specific versions
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.2")  // Compatible with compileSdk 34/35
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Minimal test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
