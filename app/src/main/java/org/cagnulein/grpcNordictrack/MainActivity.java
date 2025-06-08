package org.cagnulein.grpcNordictrack;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.ifit.glassos.workout.SpeedRequest;
import com.ifit.glassos.workout.InclineMetric;
import com.ifit.glassos.workout.InclineServiceGrpc;
import com.ifit.glassos.workout.InclineRequest;
import com.ifit.glassos.workout.WattsMetric;
import com.ifit.glassos.workout.WattsServiceGrpc;
import com.ifit.glassos.workout.ResistanceMetric;
import com.ifit.glassos.workout.ResistanceServiceGrpc;
import com.ifit.glassos.workout.ResistanceRequest;
import com.ifit.glassos.workout.CadenceMetric;
import com.ifit.glassos.workout.CadenceServiceGrpc;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String SERVER_HOST = "localhost"; // Change this to your server IP
    private static final int SERVER_PORT = 54321;
    private static final int UPDATE_INTERVAL_MS = 500; // Update every 0.5 seconds

    // UI components
    private TextView speed;
    private TextView inclination;
    private TextView watts;
    private TextView resistance;
    private TextView cadence;
    private Button speedMinus, speedPlus;
    private Button inclineMinus, inclinePlus;
    private Button resistanceMinus, resistancePlus;

    // Threading components
    private Handler mainHandler;
    private ExecutorService executorService;
    private Runnable metricsUpdateRunnable;

    // gRPC components
    private ManagedChannel channel;
    private SpeedServiceGrpc.SpeedServiceBlockingStub speedStub;
    private InclineServiceGrpc.InclineServiceBlockingStub inclineStub;
    private WattsServiceGrpc.WattsServiceBlockingStub wattsStub;
    private ResistanceServiceGrpc.ResistanceServiceBlockingStub resistanceStub;
    private CadenceServiceGrpc.CadenceServiceBlockingStub cadenceStub;

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

        // Start periodic metrics updates
        startMetricsUpdates();
    }

    // Initialize UI components
    private void initializeUI() {
        speed = findViewById(R.id.speed);
        inclination = findViewById(R.id.inclination);
        watts = findViewById(R.id.watts);
        resistance = findViewById(R.id.resistance);
        cadence = findViewById(R.id.cadence);
        
        speedMinus = findViewById(R.id.speedMinus);
        speedPlus = findViewById(R.id.speedPlus);
        inclineMinus = findViewById(R.id.inclineMinus);
        inclinePlus = findViewById(R.id.inclinePlus);
        resistanceMinus = findViewById(R.id.resistanceMinus);
        resistancePlus = findViewById(R.id.resistancePlus);
        
        // Set initial text
        speed.setText("Speed: Connecting...");
        inclination.setText("Inclination: Connecting...");
        watts.setText("Watts: Connecting...");
        resistance.setText("Resistance: Connecting...");
        cadence.setText("Cadence: Connecting...");
        
        // Set up button listeners
        setupButtonListeners();
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

        // Create the stubs
        speedStub = SpeedServiceGrpc.newBlockingStub(channel);
        inclineStub = InclineServiceGrpc.newBlockingStub(channel);
        wattsStub = WattsServiceGrpc.newBlockingStub(channel);
        resistanceStub = ResistanceServiceGrpc.newBlockingStub(channel);
        cadenceStub = CadenceServiceGrpc.newBlockingStub(channel);
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

    // Start periodic metrics updates
    private void startMetricsUpdates() {
        if (isUpdating) return;

        isUpdating = true;

        metricsUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUpdating) return;

                // Execute gRPC calls in background thread
                executorService.execute(() -> {
                    fetchAllMetricsFromServer();

                    // Schedule next update
                    if (isUpdating) {
                        mainHandler.postDelayed(metricsUpdateRunnable, UPDATE_INTERVAL_MS);
                    }
                });
            }
        };

        // Start first update
        mainHandler.post(metricsUpdateRunnable);

        Log.i(TAG, "Started periodic metrics updates");
    }

    // Stop periodic metrics updates
    private void stopMetricsUpdates() {
        isUpdating = false;

        if (metricsUpdateRunnable != null) {
            mainHandler.removeCallbacks(metricsUpdateRunnable);
        }

        Log.i(TAG, "Stopped periodic metrics updates");
    }

    // Current values for control buttons
    private volatile double currentSpeed = 0.0;
    private volatile double currentIncline = 0.0;
    private volatile double currentResistance = 0.0;

    // Setup button listeners
    private void setupButtonListeners() {
        speedMinus.setOnClickListener(v -> adjustSpeed(-0.1));
        speedPlus.setOnClickListener(v -> adjustSpeed(0.1));
        
        inclineMinus.setOnClickListener(v -> adjustIncline(-1.0));
        inclinePlus.setOnClickListener(v -> adjustIncline(1.0));
        
        resistanceMinus.setOnClickListener(v -> adjustResistance(-1.0));
        resistancePlus.setOnClickListener(v -> adjustResistance(1.0));
    }

    // Adjust speed by delta
    private void adjustSpeed(double delta) {
        executorService.execute(() -> {
            try {
                double newSpeed = Math.max(0.0, currentSpeed + delta);
                
                Metadata headers = createHeaders();
                SpeedServiceGrpc.SpeedServiceBlockingStub stubWithHeaders = speedStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                
                SpeedRequest request = SpeedRequest.newBuilder().setKph(newSpeed).build();
                stubWithHeaders.setSpeed(request);
                
                Log.d(TAG, String.format("Set speed to %.1f km/h", newSpeed));
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to set speed", e);
            }
        });
    }

    // Adjust incline by delta
    private void adjustIncline(double delta) {
        executorService.execute(() -> {
            try {
                double newIncline = Math.max(0.0, currentIncline + delta);
                
                Metadata headers = createHeaders();
                InclineServiceGrpc.InclineServiceBlockingStub stubWithHeaders = inclineStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                
                InclineRequest request = InclineRequest.newBuilder().setPercent(newIncline).build();
                stubWithHeaders.setIncline(request);
                
                Log.d(TAG, String.format("Set incline to %.1f%%", newIncline));
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to set incline", e);
            }
        });
    }

    // Adjust resistance by delta
    private void adjustResistance(double delta) {
        executorService.execute(() -> {
            try {
                double newResistance = Math.max(0.0, currentResistance + delta);
                
                Metadata headers = createHeaders();
                ResistanceServiceGrpc.ResistanceServiceBlockingStub stubWithHeaders = resistanceStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                
                ResistanceRequest request = ResistanceRequest.newBuilder().setResistance(newResistance).build();
                stubWithHeaders.setResistance(request);
                
                Log.d(TAG, String.format("Set resistance to %.0f level", newResistance));
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to set resistance", e);
            }
        });
    }

    // Create headers for gRPC calls
    private Metadata createHeaders() {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER),
                "com.ifit.eriador");
        return headers;
    }

    // Get all metrics from server
    private void fetchAllMetricsFromServer() {
        try {
            Log.d(TAG, "Making gRPC calls for all metrics...");

            Metadata headers = createHeaders();
            Empty request = Empty.newBuilder().build();

            // Fetch speed
            try {
                SpeedServiceGrpc.SpeedServiceBlockingStub speedStubWithHeaders = speedStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                SpeedMetric speedResponse = speedStubWithHeaders.getSpeed(request);
                currentSpeed = speedResponse.getLastKph();
                
                mainHandler.post(() -> {
                    speed.setText(String.format("Speed: %.1f km/h", currentSpeed));
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch speed", e);
                mainHandler.post(() -> speed.setText("Speed: Error"));
            }

            // Fetch inclination
            try {
                InclineServiceGrpc.InclineServiceBlockingStub inclineStubWithHeaders = inclineStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                InclineMetric inclineResponse = inclineStubWithHeaders.getIncline(request);
                currentIncline = inclineResponse.getLastInclinePercent();
                
                mainHandler.post(() -> {
                    inclination.setText(String.format("Inclination: %.1f%%", currentIncline));
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch inclination", e);
                mainHandler.post(() -> inclination.setText("Inclination: Error"));
            }

            // Fetch watts
            try {
                WattsServiceGrpc.WattsServiceBlockingStub wattsStubWithHeaders = wattsStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                WattsMetric wattsResponse = wattsStubWithHeaders.getWatts(request);
                double currentWatts = wattsResponse.getLastWatts();
                
                mainHandler.post(() -> {
                    watts.setText(String.format("Watts: %.0f W", currentWatts));
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch watts", e);
                mainHandler.post(() -> watts.setText("Watts: Error"));
            }

            // Fetch resistance
            try {
                ResistanceServiceGrpc.ResistanceServiceBlockingStub resistanceStubWithHeaders = resistanceStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                ResistanceMetric resistanceResponse = resistanceStubWithHeaders.getResistance(request);
                currentResistance = resistanceResponse.getLastResistance();
                
                mainHandler.post(() -> {
                    resistance.setText(String.format("Resistance: %.0f level", currentResistance));
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch resistance", e);
                mainHandler.post(() -> resistance.setText("Resistance: Error"));
            }

            // Fetch cadence
            try {
                CadenceServiceGrpc.CadenceServiceBlockingStub cadenceStubWithHeaders = cadenceStub.withInterceptors(
                        io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers)
                );
                CadenceMetric cadenceResponse = cadenceStubWithHeaders.getCadence(request);
                double currentCadence = cadenceResponse.getLastStepsPerMinute();
                
                mainHandler.post(() -> {
                    cadence.setText(String.format("Cadence: %.0f spm", currentCadence));
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch cadence", e);
                mainHandler.post(() -> cadence.setText("Cadence: Error"));
            }

            Log.d(TAG, "Completed all metrics fetch");

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch metrics", e);
            mainHandler.post(() -> {
                speed.setText("Speed: Error");
                inclination.setText("Inclination: Error");
                watts.setText("Watts: Error");
                resistance.setText("Resistance: Error");
                cadence.setText("Cadence: Error");
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop periodic updates
        stopMetricsUpdates();

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