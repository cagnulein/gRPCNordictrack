# gRPC Treadmill Integration Guide

This document provides a comprehensive guide for integrating the `GrpcTreadmillService` backend service into an Android application for GRPC-based treadmill communication.

## Architecture Overview

The application follows a separation of concerns pattern:

- **MainActivity**: Handles UI interactions and implements the `MetricsListener` interface
- **GrpcTreadmillService**: Manages all gRPC communication, SSL/TLS setup, and background operations
- **MetricsListener Interface**: Provides callback mechanism for UI updates

## Files Structure

```
app/src/main/java/org/cagnulein/grpcNordictrack/
├── MainActivity.java           # UI Activity (refactored)
└── GrpcTreadmillService.java   # Backend service (new)

app/src/main/assets/
├── client_cert.pem    # Client certificate for mTLS
├── client_key.pem     # Client private key for mTLS  
└── ca_cert.pem        # CA certificate (optional for insecure mode)
```

## Dependencies Configuration

### 1. build.gradle.kts (App Module)

Required plugins:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.protobuf") version "0.9.4"
}
```

Essential dependencies:
```kotlin
dependencies {
    // Core gRPC dependencies
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
    
    // Protobuf lite version (Android compatible)
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    
    // Required for gRPC annotations
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Standard Android dependencies
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
```

Essential protobuf configuration:
```kotlin
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
                    option("lite")  // Essential for Android
                }
            }
            task.builtins {
                id("java") {
                    option("lite")  // Essential for Android
                }
            }
        }
    }
}
```

Critical packaging configuration:
```kotlin
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
        pickFirsts += setOf(
            "**/libprotobuf-lite.so",
            "**/libprotoc.so"
        )
    }
}
```

### 2. AndroidManifest.xml

Required permissions:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

No additional configuration needed in the manifest for the service class.

## Implementation Guide

### Step 1: Add the GrpcTreadmillService Class

Create `GrpcTreadmillService.java` with the provided implementation. Key features:

- **Context-based initialization**: Requires Android Context for asset access
- **MetricsListener interface**: Provides callbacks for UI updates
- **SSL/TLS with client certificates**: Supports mutual TLS authentication
- **Background threading**: All gRPC calls executed in background threads
- **Automatic metrics polling**: Configurable update intervals
- **Graceful shutdown**: Proper cleanup of resources

### Step 2: Refactor MainActivity

Update your MainActivity to:

1. **Implement MetricsListener**:
```java
public class MainActivity extends AppCompatActivity implements GrpcTreadmillService.MetricsListener
```

2. **Initialize the service**:
```java
private void initializeBackendService() {
    treadmillService = new GrpcTreadmillService(this);
    treadmillService.setMetricsListener(this);
    
    try {
        treadmillService.initialize();
        treadmillService.startMetricsUpdates();
    } catch (Exception e) {
        // Handle initialization errors
    }
}
```

3. **Implement callback methods**:
```java
@Override
public void onSpeedUpdated(double speed) {
    speedTextView.setText(String.format("Speed: %.1f km/h", speed));
}
// ... implement other callbacks
```

4. **Handle user interactions**:
```java
speedPlusButton.setOnClickListener(v -> {
    if (treadmillService != null) {
        treadmillService.adjustSpeed(0.1);
    }
});
```

5. **Cleanup in onDestroy**:
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    if (treadmillService != null) {
        treadmillService.shutdown();
    }
}
```

### Step 3: Certificate Setup

Place the following files in `app/src/main/assets/`:

- **client_cert.pem**: Client certificate for mutual TLS
- **client_key.pem**: Client private key (PKCS#8 format)
- **ca_cert.pem**: CA certificate (optional, not used in insecure mode)

## Service API Reference

### Constructor
```java
GrpcTreadmillService(Context context)
```

### Key Methods
```java
void setMetricsListener(MetricsListener listener)  // Set UI callback listener
void initialize() throws Exception                 // Initialize gRPC connection
void startMetricsUpdates()                        // Begin periodic metrics polling
void stopMetricsUpdates()                         // Stop metrics polling
void adjustSpeed(double delta)                    // Adjust speed by delta km/h
void adjustIncline(double delta)                  // Adjust incline by delta %
void adjustResistance(double delta)               // Adjust resistance by delta level
void shutdown()                                   // Cleanup all resources
```

### MetricsListener Interface
```java
interface MetricsListener {
    void onSpeedUpdated(double speed);
    void onInclineUpdated(double incline);
    void onWattsUpdated(double watts);
    void onResistanceUpdated(double resistance);
    void onCadenceUpdated(double cadence);
    void onError(String metric, String error);
}
```

## Configuration Options

### Server Connection
Update these constants in `GrpcTreadmillService.java`:
```java
private static final String SERVER_HOST = "localhost";  // Change to your server
private static final int SERVER_PORT = 54321;           // Change to your port
private static final int UPDATE_INTERVAL_MS = 500;      // Polling interval
```

### Client ID Header
The service sends a client ID header with each request:
```java
private Metadata createHeaders() {
    Metadata headers = new Metadata();
    headers.put(Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER),
            "com.ifit.eriador");  // Change as needed
    return headers;
}
```

## Security Considerations

- The service uses **client certificate authentication** for secure communication
- Server certificate validation is **disabled** (insecure mode) for development
- For production, implement proper server certificate validation
- Keep certificate files secure and consider using Android Keystore for private keys

## Error Handling

The service provides comprehensive error handling:

- **Connection errors**: Reported via `onError()` callback
- **Certificate errors**: Throw exceptions during initialization
- **Individual metric failures**: Isolated per metric, others continue working
- **Graceful degradation**: UI shows error messages for failed metrics

## Threading Model

- **Main Thread**: UI updates and service initialization
- **Background Thread**: All gRPC communication and SSL operations
- **Handler-based callbacks**: Ensures UI updates occur on main thread
- **ExecutorService**: Single background thread for all network operations

## Troubleshooting

### Common Build Issues
1. **Protobuf conflicts**: Ensure lite versions are used everywhere
2. **Duplicate META-INF files**: Configure packaging excludes
3. **Missing annotations**: Add javax.annotation-api dependency

### Runtime Issues
1. **Certificate not found**: Verify files are in assets folder
2. **Connection timeout**: Check server host/port configuration
3. **SSL handshake failure**: Verify certificate format and compatibility

### Testing
- Use Android Emulator or device with network connectivity
- Verify server is running and accessible
- Check certificate file formats and paths
- Monitor logs for detailed error information

## Benefits of This Architecture

1. **Separation of Concerns**: UI logic separate from network logic
2. **Testability**: Service can be unit tested independently
3. **Reusability**: Service can be used in multiple activities
4. **Maintainability**: Clear interfaces and responsibilities
5. **Error Isolation**: Network errors don't crash the UI
6. **Threading Safety**: Proper background/UI thread management