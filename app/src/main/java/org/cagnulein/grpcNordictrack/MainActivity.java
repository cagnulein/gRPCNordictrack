package org.cagnulein.grpcNordictrack;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements GrpcTreadmillService.MetricsListener {

    private static final String TAG = "MainActivity";

    // UI components
    private TextView speed;
    private TextView inclination;
    private TextView watts;
    private TextView resistance;
    private TextView cadence;
    private Button speedMinus, speedPlus;
    private Button inclineMinus, inclinePlus;
    private Button resistanceMinus, resistancePlus;

    // Backend service
    private GrpcTreadmillService treadmillService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        initializeUI();

        // Initialize and start backend service
        initializeBackendService();
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

    // Initialize backend service
    private void initializeBackendService() {
        treadmillService = new GrpcTreadmillService(this);
        treadmillService.setMetricsListener(this);
        
        try {
            treadmillService.initialize();
            treadmillService.startMetricsUpdates();
            Log.i(TAG, "Backend service initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize backend service", e);
            speed.setText("Speed: Connection Failed");
            inclination.setText("Inclination: Connection Failed");
            watts.setText("Watts: Connection Failed");
            resistance.setText("Resistance: Connection Failed");
            cadence.setText("Cadence: Connection Failed");
        }
    }

    // Setup button listeners
    private void setupButtonListeners() {
        speedMinus.setOnClickListener(v -> {
            if (treadmillService != null) {
                treadmillService.adjustSpeed(-0.1);
            }
        });
        speedPlus.setOnClickListener(v -> {
            if (treadmillService != null) {
                treadmillService.adjustSpeed(0.1);
            }
        });
        
        inclineMinus.setOnClickListener(v -> {
            if (treadmillService != null) {
                treadmillService.adjustIncline(-1.0);
            }
        });
        inclinePlus.setOnClickListener(v -> {
            if (treadmillService != null) {
                treadmillService.adjustIncline(1.0);
            }
        });
        
        resistanceMinus.setOnClickListener(v -> {
            if (treadmillService != null) {
                treadmillService.adjustResistance(-1.0);
            }
        });
        resistancePlus.setOnClickListener(v -> {
            if (treadmillService != null) {
                treadmillService.adjustResistance(1.0);
            }
        });
    }

    // MetricsListener interface implementation
    @Override
    public void onSpeedUpdated(double speedValue) {
        speed.setText(String.format("Speed: %.1f km/h", speedValue));
    }

    @Override
    public void onInclineUpdated(double inclineValue) {
        inclination.setText(String.format("Inclination: %.1f%%", inclineValue));
    }

    @Override
    public void onWattsUpdated(double wattsValue) {
        watts.setText(String.format("Watts: %.0f W", wattsValue));
    }

    @Override
    public void onResistanceUpdated(double resistanceValue) {
        resistance.setText(String.format("Resistance: %.0f level", resistanceValue));
    }

    @Override
    public void onCadenceUpdated(double cadenceValue) {
        cadence.setText(String.format("Cadence: %.0f spm", cadenceValue));
    }

    @Override
    public void onError(String metric, String error) {
        switch (metric) {
            case "speed":
                speed.setText("Speed: " + error);
                break;
            case "inclination":
                inclination.setText("Inclination: " + error);
                break;
            case "watts":
                watts.setText("Watts: " + error);
                break;
            case "resistance":
                resistance.setText("Resistance: " + error);
                break;
            case "cadence":
                cadence.setText("Cadence: " + error);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Shutdown backend service
        if (treadmillService != null) {
            treadmillService.shutdown();
        }
    }
}