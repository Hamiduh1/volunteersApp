package com.example.volunteersApp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
// import com.google.android.gms.tasks.OnSuccessListener; // Already implicitly imported by FusedLocationProviderClient usage
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private GoogleMap mMap;
    private EditText slocationEditText;
    private Button frontButton;
    private Button backButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String eventId;
    private String currentUserId;
    // private String organizerIdFromIntent; // Declare if you plan to use it

    private LatLng lastSearchedLatLng;
    private Marker currentMapMarker;
    private FusedLocationProviderClient fusedLocationClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps); // Ensure this layout is correct

        slocationEditText = findViewById(R.id.searchlocation); // From activity_maps.xml
        frontButton = findViewById(R.id.FinalStep);        // From activity_maps.xml
        backButton = findViewById(R.id.GoBack);            // From activity_maps.xml
        // Ensure R.id.search_button is also correctly assigned if it's separate from onMapSearch in XML

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated. Cannot proceed.");
            Toast.makeText(this, "Authentication required.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        eventId = getIntent().getStringExtra("EventId");
        // organizerIdFromIntent = getIntent().getStringExtra("organizerId"); // Retrieve if passed and needed

        if (eventId == null || eventId.trim().isEmpty()) {
            Log.e(TAG, "EventId not provided in Intent. Cannot save location.");
            Toast.makeText(this, "Error: Event ID missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map); // From activity_maps.xml
        if (mapFragment == null) {
            Log.e(TAG, "MapFragment not found in R.id.map!");
            Toast.makeText(this, "Error initializing map.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mapFragment.getMapAsync(this);

        frontButton.setOnClickListener(view -> saveLocationToFirestore());
        backButton.setOnClickListener(view -> {
            // Intent i = new Intent(view.getContext(), HostAnEvent.class); // Or previous screen
            // if (eventId != null && !eventId.isEmpty()) {
            //    i.putExtra("EventId", eventId);
            // }
            // i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // startActivity(i);
            finish(); // Or use ActivityResultLauncher to return to previous if it's more complex
        });

        // If your search button in XML has android:onClick="onMapSearch"
        // Button searchButtonView = findViewById(R.id.search_button); // from activity_maps.xml
        // searchButtonView.setOnClickListener(this::onMapSearch); // Alternative to XML onClick
    }

    private void saveLocationToFirestore() {
        String locationString = slocationEditText.getText().toString().trim();
        if (locationString.isEmpty() && lastSearchedLatLng == null) {
            slocationEditText.setError("Location cannot be empty");
            slocationEditText.requestFocus();
            Toast.makeText(this, "Please enter or search for a location.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "EventId is null or empty, cannot save location.");
            Toast.makeText(this, "Error: Event ID missing. Cannot save.", Toast.LENGTH_LONG).show();
            return;
        }

        DocumentReference eventDocRef = db.collection("events").document(eventId);
        Map<String, Object> locationData = new HashMap<>();

        if (!locationString.isEmpty()) {
            locationData.put("locationString", locationString);
        } else if (lastSearchedLatLng != null && currentMapMarker != null && currentMapMarker.getTitle() != null) {
            locationData.put("locationString", currentMapMarker.getTitle());
        }

        if (lastSearchedLatLng == null && !locationString.isEmpty()) {
            Log.d(TAG, "Attempting to geocode locationString before saving: " + locationString);
            Geocoder geocoder = new Geocoder(this);
            try {
                List<Address> addresses = geocoder.getFromLocationName(locationString, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    lastSearchedLatLng = new LatLng(address.getLatitude(), address.getLongitude());
                } else {
                    Toast.makeText(this, "Could not verify address: " + locationString + ". Saving text only.", Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException during geocoding on save for: " + locationString, e);
                Toast.makeText(this, "Network error. Saving text only.", Toast.LENGTH_LONG).show();
            }
        }

        if (lastSearchedLatLng != null) {
            GeoPoint geoPoint = new GeoPoint(lastSearchedLatLng.latitude, lastSearchedLatLng.longitude);
            locationData.put("locationCoordinates", geoPoint);
        }

        if (locationData.isEmpty() || (!locationData.containsKey("locationString") && !locationData.containsKey("locationCoordinates"))) {
            Toast.makeText(this, "No location information to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        frontButton.setEnabled(false);
        backButton.setEnabled(false);

        eventDocRef.update(locationData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Location successfully updated in Firestore for event: " + eventId);
                    Toast.makeText(MapsActivity.this, "Location saved!", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(MapsActivity.this, HostFinal.class); // Navigate to HostFinal Activity
                    i.putExtra("EventId", eventId);
                    // Add any other flags or extras HostFinal might need
                    startActivity(i);
                    finish(); // Finish MapsActivity
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating location in Firestore for event: " + eventId, e);
                    Toast.makeText(MapsActivity.this, "Failed to save location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    frontButton.setEnabled(true);
                    backButton.setEnabled(true);
                });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            updateMapLocation(currentLocation, "Current Location", 15);
                        } else {
                            LatLng defaultLocation = new LatLng(39.9526, -75.1652); // Philadelphia
                            updateMapLocation(defaultLocation, "Default Location", 10);
                        }
                    });
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            LatLng defaultLocation = new LatLng(39.9526, -75.1652); // Philadelphia
            updateMapLocation(defaultLocation, "Default Location", 10);
        }
        // TODO: If editing, fetch and display existing event location from Firestore.
    }

    private void updateMapLocation(LatLng latLng, String title, float zoomLevel) {
        if (mMap == null) return;
        if (currentMapMarker != null) {
            currentMapMarker.remove();
        }
        MarkerOptions markerOptions = new MarkerOptions().position(latLng).title(title);
        currentMapMarker = mMap.addMarker(markerOptions);
        if (currentMapMarker != null) currentMapMarker.showInfoWindow();
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel));
        lastSearchedLatLng = latLng;

        if (!title.equals("Current Location") && !title.equals("Default Location")) {
            slocationEditText.setText(title);
        } else {
            slocationEditText.setText("");
        }
    }

    // This method is called if you have android:onClick="onMapSearch" in your activity_maps.xml for the search button
    public void onMapSearch(View view) {
        if (mMap == null) return;
        String locationName = slocationEditText.getText().toString().trim();
        if (locationName.isEmpty()) {
            slocationEditText.setError("Please enter a location to search");
            slocationEditText.requestFocus();
            return;
        }

        List<Address> addressList = null;
        Geocoder geocoder = new Geocoder(this);
        try {
            addressList = geocoder.getFromLocationName(locationName, 1);
        } catch (IOException e) {
            Log.e(TAG, "Geocoding failed for: " + locationName, e);
            Toast.makeText(this, "Error searching location. Check network.", Toast.LENGTH_SHORT).show();
        }

        if (addressList == null || addressList.isEmpty()) {
            slocationEditText.setError("Invalid location or no results found");
            Toast.makeText(this, "Location not found: " + locationName, Toast.LENGTH_SHORT).show();
            lastSearchedLatLng = null;
            if (currentMapMarker != null) {
                currentMapMarker.remove();
                currentMapMarker = null;
            }
        } else {
            Address address = addressList.get(0);
            LatLng foundLatLng = new LatLng(address.getLatitude(), address.getLongitude());
            String addressText = address.getAddressLine(0) != null ? address.getAddressLine(0) : locationName;
            updateMapLocation(foundLatLng, addressText, 15);
            slocationEditText.setText(addressText);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if(mMap != null) onMapReady(mMap); // Re-initialize map to get location
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
