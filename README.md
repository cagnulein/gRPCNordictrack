# gRPC Treadmill Android App

An Android application for monitoring treadmill speed via gRPC communication, based on the work from [treadmillcontrol](https://github.com/descartesbeforedahorse/treadmillcontrol).

## Overview

This Android app connects to a gRPC service to retrieve real-time speed data from a treadmill and displays it in a clean, simple interface. The app updates the speed every second and includes both production and testing modes.

## Features

- **Real-time Speed Display**: Shows current treadmill speed in km/h
- **gRPC Communication**: Uses efficient gRPC protocol for server communication
- **TLS/SSL Support**: Secure communication with certificate-based authentication
- **Error Handling**: Robust error handling with user-friendly error messages
- **Lifecycle Management**: Proper resource cleanup and connection management

## Architecture

- **UI Layer**: Simple TextView displaying speed information
- **Network Layer**: gRPC client using OkHttp for Android compatibility
- **Threading**: Proper background threading for network operations

## Setup

### Prerequisites

- Android Studio Arctic Fox or newer
- Android SDK API level 26 or higher
- Java 8 or higher

### Dependencies

The project uses the following main dependencies:

```kotlin
// gRPC
implementation("io.grpc:grpc-okhttp:1.63.0")
implementation("io.grpc:grpc-protobuf-lite:1.63.0")
implementation("io.grpc:grpc-stub:1.63.0")

// Protocol Buffers
implementation("com.google.protobuf:protobuf-javalite:3.25.3")
```

### Installation

1. Clone this repository
2. Open the project in Android Studio
3. Sync the project with Gradle files
4. Build and run on your device or emulator

## Configuration

### Server Connection

Update the connection settings in `MainActivity.java`:

```java
private static final String SERVER_HOST = "your.server.address";
private static final int SERVER_PORT = 54321;
```

### TLS Certificates

For secure connections, place your certificates in `app/src/main/assets/`:

- `ca_cert.pem` - Certificate Authority certificate
- `client_cert.pem` - Client certificate
- `client_key.pem` - Client private key

## Project Structure

```
app/src/main/java/org/cagnulein/grpctreadmill/
└── MainActivity.java          # Main activity with gRPC client

app/src/main/assets/
├── ca_cert.pem               # CA certificate (optional)
├── client_cert.pem           # Client certificate (optional)
└── client_key.pem            # Client private key (optional)
```

## Protocol Buffers

The app expects the following gRPC service definition:

```protobuf
service SpeedService {
  rpc GetSpeed(Empty) returns (SpeedMetric);
}

message SpeedMetric {
  double lastKph = 1;
}

message Empty {}
```

## Building

### Debug Build

```bash
./gradlew assembleDebug
```

### Release Build

```bash
./gradlew assembleRelease
```

## Credits

This project is based on the excellent work from [treadmillcontrol](https://github.com/descartesbeforedahorse/treadmillcontrol) by descartesbeforedahorse.

## License

This project follows the same license as the original treadmillcontrol project.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Troubleshooting

### Common Issues

**Connection Errors**
- Verify server address and port
- Check network connectivity
- Ensure firewall allows the connection

**Build Errors**
- Clean and rebuild the project
- Verify all dependencies are properly synced
- Check that Protocol Buffers are correctly generated

**Certificate Issues**
- Verify certificate files are in the correct assets folder
- Check certificate format (PEM)
- Ensure certificates are not expired

### Logging

Enable verbose logging by checking Android Studio's Logcat with filter:

```
MainActivity
```

## Support

For issues related to the gRPC protocol or treadmill communication, refer to the original [treadmillcontrol](https://github.com/descartesbeforedahorse/treadmillcontrol) project.

For Android-specific issues, please open an issue in this repository.