package com.upjv.rla;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.os.SystemClock;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.View;

import com.upjv.rla.databinding.ActivityMainBinding;
import com.upjv.rla.utils.PIDController;

import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private final int REQUEST_LOCATION_PERMISSION = 1;
    
    private ActivityMainBinding binding;

    private TextView distanceView;
    private TextView speedView;
    private TextView stopwatchView;
    private Button startButton;
    private Button settingsButton;
    private ProgressBar throttleView;
    private MapView mapView;
    private Polyline tripLine;


    private LocationManager locationManager;
    private String bestProvider;

    private Location lastLocation;
    private boolean isTracking = false;
    private float traveledDistance = 0;
    private long startTime = 0;
    private long elapsedTime = 0;
    private long setTime = 60000;
    private float setDistance = 400;
    private float currentSpeed = 0;

    private PIDController pidController = new PIDController(1, 1 , 1, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        distanceView = (TextView) findViewById(R.id.distance_text);
        speedView = (TextView) findViewById(R.id.speed_text);
        stopwatchView = (TextView) findViewById(R.id.stopawatch_text);
        throttleView = (ProgressBar) findViewById(R.id.throttle_bar);

        startButton = (Button) findViewById(R.id.start_button);
        settingsButton = (Button) findViewById(R.id.settings_button);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        bestProvider = locationManager.getBestProvider(new Criteria(), true);
        requestLocationPermission();

        IConfigurationProvider osmConf = Configuration.getInstance();
        File basePath = new File(getCacheDir().getAbsolutePath(), "osmdroid");
        osmConf.setOsmdroidBasePath(basePath);
        File tileCache = new File(basePath.getAbsolutePath(), "tile");
        osmConf.setOsmdroidTileCache(tileCache);
        osmConf.setUserAgentValue("Android/1.0");

        mapView = (MapView)findViewById(R.id.mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(18.);

        tripLine = new Polyline();;
        mapView.getOverlays().add(tripLine);
        mapView.invalidate();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isTracking) {
                    startTracking();
                } else {
                    stopTracking();
                }
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    public void onLocationChanged(Location location) {
        if (lastLocation != null) traveledDistance += location.distanceTo(lastLocation);
        lastLocation = location;
        currentSpeed = lastLocation.getSpeed();

        tripLine.addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
        mapView.invalidate();
        mapView.getController().setCenter(new GeoPoint(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(REQUEST_LOCATION_PERMISSION)
    public void requestLocationPermission() {
        String[] perms = {Manifest.permission.ACCESS_FINE_LOCATION};
        if(EasyPermissions.hasPermissions(this, perms)) {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
        else {
            EasyPermissions.requestPermissions(this, "Please grant the location permission", REQUEST_LOCATION_PERMISSION, perms);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTracking();
    }


    @SuppressLint("MissingPermission")
    public void startTracking() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            String[] split = sharedPreferences.getString("time", "01:00.00").split(":");
            long minutes = Integer.parseInt(split[0]);
            float seconds = Float.parseFloat(split[1]);
            setTime = (long) (minutes * 60000 + seconds * 1000);
            setDistance = Integer.parseInt(sharedPreferences.getString("distance", "400"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        lastLocation = null;
        traveledDistance = 0;
        currentSpeed = 0;
        tripLine.setPoints(new ArrayList<GeoPoint>());
        mapView.invalidate();

        isTracking = true;
        locationManager.requestLocationUpdates(bestProvider, 0, 0, this);

        startTime = SystemClock.elapsedRealtime();
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                elapsedTime = SystemClock.elapsedRealtime() - startTime;
                int centiseconds = (int) (elapsedTime / 10) % 100;
                int seconds = (int) (elapsedTime / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                stopwatchView.setText(String.format("%d:%02d.%02d", minutes, seconds, centiseconds));

                float remainingDistance = setDistance - traveledDistance;
                long remainingTime = Math.max(0, setTime - elapsedTime);

                double desiredSpeed = remainingDistance / remainingTime * 1000;
                if (remainingDistance <= 0) {
                    desiredSpeed = 0;
                    stopTracking();
                }

                pidController.setDesiredSpeed(desiredSpeed);
                double throttle = pidController.getOutput(currentSpeed);

                distanceView.setText(String.format("%02.1f m", traveledDistance));
                speedView.setText(String.format("%02.1f km/h", currentSpeed * 3.6));
                throttleView.setProgress((int) throttle);

                if (isTracking) {
                    handler.postDelayed(this, 10);
                }
            }
        });

        startButton.setText("Stop");
    }

    public void stopTracking() {
        isTracking = false;
        locationManager.removeUpdates(this);
        pidController.reset();
        startButton.setText("Start");
    }
}