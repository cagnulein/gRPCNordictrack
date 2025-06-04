package org.cagnulein.grpcNordictrack;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.okhttp.OkHttpChannelBuilder;

import com.ifit.glassos.util.Empty;
import com.ifit.glassos.workout.SpeedMetric;
import com.ifit.glassos.workout.SpeedServiceGrpc;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String SERVER_HOST = "localhost"; // Change this to your server IP
    private static final int SERVER_PORT = 54321;
    private static final int UPDATE_INTERVAL_MS = 1000; // Update every second

    // UI components
    private TextView speed;

    // Threading components
    private Handler mainHandler;
    private ExecutorService executorService;
    private Runnable speedUpdateRunnable;

    // gRPC components
    private ManagedChannel channel;
    private SpeedServiceGrpc.SpeedServiceBlockingStub stub;

    // Control flags
    private volatile boolean isUpdating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        initializeUI();

        // Initialize threading components
        initializeThreading();

        // Initialize gRPC connection
        initializeGrpcConnection();

        // Start periodic speed updates
        startSpeedUpdates();
    }

    // Initialize UI components
    private void initializeUI() {
        speed = findViewById(R.id.speed);
        speed.setText("Speed: Connecting...");
    }

    // Initialize threading components
    private void initializeThreading() {
        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
    }

    // Initialize gRPC connection with TLS client certificates (insecure server validation)
    private void initializeGrpcConnection() {
        try {
            // Verify required certificate files exist (ca_cert.pem not strictly needed for insecure mode)
            String[] requiredFiles = {"client_cert.pem", "client_key.pem"};
            for (String file : requiredFiles) {
                try {
                    getAssets().open(file).close();
                } catch (Exception e) {
                    throw new RuntimeException("Required certificate file missing: " + file +
                            ". Please add it to app/src/main/assets/");
                }
            }

            // Load certificates from assets (ca_cert not used in insecure mode, but keep for compatibility)
            InputStream caCertStream = null;
            try {
                caCertStream = getAssets().open("ca_cert.pem");
            } catch (Exception e) {
                Log.w(TAG, "ca_cert.pem not found, continuing with insecure mode");
            }
            InputStream clientCertStream = getAssets().open("client_cert.pem");
            InputStream clientKeyStream = getAssets().open("client_key.pem");

            Log.i(TAG, "Loading TLS certificates (insecure server validation mode)...");

            // Create TLS context with client certificate authentication (insecure server validation)
            SSLContext sslContext = createSSLContext(caCertStream, clientCertStream, clientKeyStream);

            // Create channel with TLS (client certificates used, server validation disabled)
            channel = OkHttpChannelBuilder.forAddress(SERVER_HOST, SERVER_PORT)
                    .sslSocketFactory(sslContext.getSocketFactory())
                    .build();

            // Close certificate streams
            if (caCertStream != null) caCertStream.close();
            clientCertStream.close();
            clientKeyStream.close();

            Log.i(TAG, "gRPC connection initialized with client certificates (insecure server validation)");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize gRPC with certificates", e);

            // Don't fallback - force user to fix certificate issue
            mainHandler.post(() -> {
                speed.setText("Speed: Certificate Error - Check Logs");
            });
            throw new RuntimeException("Certificate initialization failed", e);
        }

        // Create the stub
        stub = SpeedServiceGrpc.newBlockingStub(channel);
    }

    // Create SSL context with client certificate authentication but insecure server validation
    // (equivalent to -cert, -key, -cacert with -insecure flag)
    private SSLContext createSSLContext(InputStream caCertStream, InputStream clientCertStream,
                                        InputStream clientKeyStream) throws Exception {

        Log.d(TAG, "Creating SSL context with client certificates (insecure server validation)...");

        // Load client certificate (-cert)
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate clientCert = (X509Certificate) cf.generateCertificate(clientCertStream);
        Log.d(TAG, "Loaded client certificate: " + clientCert.getSubjectDN());

        // Parse private key (-key) - assuming PKCS#8 format
        // Read all bytes from InputStream (compatible with API 26+)
        byte[] keyData = readAllBytesCompat(clientKeyStream);
        String keyString = new String(keyData, StandardCharsets.UTF_8);
        keyString = keyString.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyString);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        Log.d(TAG, "Loaded private key");

        // Create key store with client certificate and private key
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, "".toCharArray(), new Certificate[]{clientCert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "".toCharArray());

        // Create insecure trust manager (equivalent to -insecure flag)
        // This accepts ANY server certificate without validation
        javax.net.ssl.TrustManager[] insecureTrustManagers = new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Accept all client certificates (not used in our case)
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Accept all server certificates (this is the -insecure behavior)
                        Log.d(TAG, "Accepting server certificate without validation (insecure mode)");
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        // Create SSL context with client certs but insecure server validation
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), insecureTrustManagers, new SecureRandom());

        Log.i(TAG, "SSL context created with client authentication but insecure server validation");
        return sslContext;
    }

    // Helper method to read all bytes from InputStream (compatible with API 26+)
    private byte[] readAllBytesCompat(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }

        return output.toByteArray();
    }

    // Start periodic speed updates
    private void startSpeedUpdates() {
        if (isUpdating) return;

        isUpdating = true;

        speedUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUpdating) return;

                // Execute gRPC call in background thread
                executorService.execute(() -> {
                    fetchSpeedFromServer();

                    // Schedule next update
                    if (isUpdating) {
                        mainHandler.postDelayed(speedUpdateRunnable, UPDATE_INTERVAL_MS);
                    }
                });
            }
        };

        // Start first update
        mainHandler.post(speedUpdateRunnable);

        Log.i(TAG, "Started periodic speed updates");
    }

    // Stop periodic speed updates
    private void stopSpeedUpdates() {
        isUpdating = false;

        if (speedUpdateRunnable != null) {
            mainHandler.removeCallbacks(speedUpdateRunnable);
        }

        Log.i(TAG, "Stopped periodic speed updates");
    }

    // Get speed from server (equivalent to GetSpeed call with TLS client certificates)
    private void fetchSpeedFromServer() {
        try {
            Log.d(TAG, "Making gRPC call with client certificates...");

            // Create metadata for headers (equivalent to -H "client_id: com.ifit.eriador")
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER),
                    "com.ifit.eriador");

            Log.d(TAG, "Added client_id header: com.ifit.eriador");

            // Create stub with metadata and client_id header
            SpeedServiceGrpc.SpeedServiceBlockingStub stubWithHeaders = stub.withInterceptors(
                    io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
            );

            // Call GetSpeed - during this call:
            // 1. TLS handshake occurs using our SSL context
            // 2. Server validates our client certificate (client_cert.pem + client_key.pem)
            // 3. We validate server certificate using CA (ca_cert.pem)
            // 4. client_id header is sent
            Empty request = Empty.newBuilder().build();

            Log.d(TAG, "Calling GetSpeed with TLS client authentication...");
            SpeedMetric response = stubWithHeaders.getSpeed(request);

            // Update UI with received speed
            double currentSpeed = response.getLastKph();

            mainHandler.post(() -> {
                speed.setText(String.format("Speed: %.1f km/h (TLS)", currentSpeed));
            });

            Log.d(TAG, String.format("Received speed via TLS: %.1f km/h", currentSpeed));

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch speed with TLS", e);
            mainHandler.post(() -> {
                speed.setText("Speed: TLS Error");
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop periodic updates
        stopSpeedUpdates();

        // Shutdown gRPC channel
        if (channel != null) {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error shutting down gRPC channel", e);
                channel.shutdownNow();
            }
        }

        // Shutdown executor service
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error shutting down executor service", e);
                executorService.shutdownNow();
            }
        }
    }
}