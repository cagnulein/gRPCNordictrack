package org.cagnulein.grpcNordictrack;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.util.Base64;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;

import com.ifit.glassos.util.Empty;
import com.ifit.glassos.workout.SpeedMetric;
import com.ifit.glassos.workout.SpeedServiceGrpc;

import org.cagnulein.grpctreadmill.R;

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
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI components
        initializeUI();

        // Initialize threading components
        initializeThreading();

        // Initialize gRPC connection with TLS
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

    // Initialize gRPC connection with TLS certificates
    private void initializeGrpcConnection() {
        executorService.execute(() -> {
            try {
                // Create SSL socket factory with certificates
                SSLContext sslContext = buildSslContext();

                // Create gRPC channel with TLS using OkHttp (Android compatible)
                channel = OkHttpChannelBuilder.forAddress(SERVER_HOST, SERVER_PORT)
                        .sslSocketFactory(sslContext.getSocketFactory())
                        .build();

                // Create service stub
                stub = SpeedServiceGrpc.newBlockingStub(channel);

                // Update UI on successful connection
                mainHandler.post(() -> {
                    speed.setText("Speed: Connected");
                    Log.i(TAG, "gRPC connection established successfully");
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize gRPC connection", e);
                mainHandler.post(() -> {
                    speed.setText("Speed: Connection Error");
                });
            }
        });
    }

    // Build SSL context from certificate files in assets
    private SSLContext buildSslContext() throws Exception {
        // Load CA certificate
        InputStream caCertStream = getAssets().open("ca_cert.pem");
        Certificate caCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(caCertStream);
        caCertStream.close();

        // Load client certificate
        InputStream clientCertStream = getAssets().open("client_cert.pem");
        Certificate clientCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(clientCertStream);
        clientCertStream.close();

        // Load client private key
        InputStream clientKeyStream = getAssets().open("client_key.pem");
        PrivateKey clientKey = loadPrivateKey(clientKeyStream);
        clientKeyStream.close();

        // Create trust store with CA certificate
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);

        // Create key store with client certificate and key
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", clientKey, "".toCharArray(), new Certificate[]{clientCert});

        // Initialize trust manager factory
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        // Initialize key manager factory
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "".toCharArray());

        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(), null);

        return sslContext;
    }

    // Load private key from PEM format
    private PrivateKey loadPrivateKey(InputStream keyStream) throws Exception {
        // Read the key file content
        byte[] keyBytes = new byte[keyStream.available()];
        keyStream.read(keyBytes);
        String keyContent = new String(keyBytes);

        // Remove PEM headers and decode Base64
        keyContent = keyContent.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decodedKey = Base64.getDecoder().decode(keyContent);

        // Create private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    // Start periodic speed updates
    private void startSpeedUpdates() {
        isUpdating = true;
        speedUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isUpdating) {
                    updateSpeed();
                    // Schedule next update
                    mainHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
        // Start the periodic updates
        mainHandler.postDelayed(speedUpdateRunnable, UPDATE_INTERVAL_MS);
    }

    // Update speed from gRPC service
    private void updateSpeed() {
        executorService.execute(() -> {
            try {
                if (stub != null) {
                    // Get speed from service
                    SpeedMetric speedMetric = stub.getSpeed(Empty.newBuilder().build());
                    double lastKph = speedMetric.getLastKph();

                    // Update UI on main thread
                    mainHandler.post(() -> {
                        speed.setText(String.format("Speed: %.1f km/h", lastKph));
                    });

                    Log.d(TAG, "Speed updated: " + lastKph + " km/h");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting speed from service", e);
                // Update UI on main thread
                mainHandler.post(() -> {
                    speed.setText("Speed: Service Error");
                });
            }
        });
    }

    // Stop periodic speed updates
    private void stopSpeedUpdates() {
        isUpdating = false;
        if (mainHandler != null && speedUpdateRunnable != null) {
            mainHandler.removeCallbacks(speedUpdateRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop updates when app goes to background
        stopSpeedUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume updates when app comes to foreground
        if (!isUpdating && stub != null) {
            startSpeedUpdates();
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