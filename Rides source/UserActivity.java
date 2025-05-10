package com.z_iti_271311_u2_ind_14;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;

import java.util.HashMap;
import java.util.Map;

public class UserActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private EditText etUsername;
    private Button btnSend;
    private Marker currentMarker;
    private final LatLng INITIAL_LOCATION = new LatLng(23.76282137431006, -99.13967683911324);
    private DatabaseReference mDatabase;
    private GeoApiContext geoApiContext;
    private TextView tvEstimatedTime;
    private final LatLng CONDUCTOR_INICIAL = new LatLng(23.76282137431006, -99.13967683911324);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        // Inicializar Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference("ubicaciones");
        // Inicializar vistas
        etUsername = findViewById(R.id.etUsername);
        btnSend = findViewById(R.id.btnSend);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        // Obtener el fragmento del mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Configurar el botón de enviar
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enviarDatos();
            }
        });
        geoApiContext = new GeoApiContext.Builder()
                .apiKey("your-api-key")
                .build();
    }

    private void calcularTiempoEstimado(LatLng ubicacionUsuario) {
        new AsyncTask<LatLng, Void, String>() {
            @Override
            protected String doInBackground(LatLng... params) {
                try {
                    DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                            .origin(new com.google.maps.model.LatLng(
                                    CONDUCTOR_INICIAL.latitude,
                                    CONDUCTOR_INICIAL.longitude))
                            .destination(new com.google.maps.model.LatLng(
                                    params[0].latitude,
                                    params[0].longitude))
                            .mode(TravelMode.DRIVING)
                            .await();

                    if (result.routes.length > 0) {
                        DirectionsRoute route = result.routes[0];
                        long segundos = route.legs[0].duration.inSeconds;

                        // Convertir a minutos
                        long minutos = segundos / 60;

                        if (minutos < 60) {
                            return minutos + " minutos";
                        } else {
                            long horas = minutos / 60;
                            minutos = minutos % 60;
                            return horas + " horas " + minutos + " minutos";
                        }
                    }
                } catch (Exception e) {
                    Log.e("UserActivity", "Error calculando tiempo", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String tiempoEstimado) {
                if (tiempoEstimado != null) {
                    tvEstimatedTime.setVisibility(View.VISIBLE);
                    tvEstimatedTime.setText("Tiempo estimado de llegada: " + tiempoEstimado);
                } else {
                    tvEstimatedTime.setVisibility(View.VISIBLE);
                    tvEstimatedTime.setText("No se pudo calcular el tiempo estimado");
                }
            }
        }.execute(ubicacionUsuario);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(INITIAL_LOCATION, 15f));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull LatLng latLng) {
                if (currentMarker != null) {
                    currentMarker.remove();
                }

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(latLng)
                        .title("Mi ubicación")
                        .draggable(true);

                currentMarker = mMap.addMarker(markerOptions);

                // Calcular tiempo estimado
                calcularTiempoEstimado(latLng);
            }
        });

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {}

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {}

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                currentMarker = marker;
            }
        });
    }

    private void enviarDatos() {
        String username = etUsername.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Por favor ingresa un nombre");
            return;
        }

        if (currentMarker == null) {
            Toast.makeText(this, "Por favor selecciona una ubicación en el mapa",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Obtener la ubicación seleccionada
        LatLng ubicacion = currentMarker.getPosition();

        // Crear objeto con los datos
        Map<String, Object> datosUsuario = new HashMap<>();
        datosUsuario.put("nombre", username);
        datosUsuario.put("latitud", ubicacion.latitude);
        datosUsuario.put("longitud", ubicacion.longitude);
        datosUsuario.put("timestamp", ServerValue.TIMESTAMP); // Usar timestamp del servidor
        datosUsuario.put("estado", "pendiente");

        // Generar una key única para cada ubicación
        String locationKey = mDatabase.push().getKey();

        if (locationKey != null) {
            // Enviar datos a Firebase
            mDatabase.child(locationKey).setValue(datosUsuario)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("UserActivity", "Ubicación enviada exitosamente: " + locationKey);
                        Toast.makeText(UserActivity.this,
                                "Ubicación enviada exitosamente", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("UserActivity", "Error al enviar ubicación", e);
                        Toast.makeText(UserActivity.this,
                                "Error al enviar ubicación: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }
}