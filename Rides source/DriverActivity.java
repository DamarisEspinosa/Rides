package com.z_iti_271311_u2_ind_14;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.google.firebase.database.*;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import android.os.AsyncTask;
import android.widget.Toast;
import java.util.*;

public class DriverActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "DriverActivity";
    private GoogleMap mMap;
    private final LatLng PUNTO_INICIAL = new LatLng(23.76282137431006, -99.13967683911324);
    private final LatLng PUNTO_FINAL = new LatLng(23.7283452, -99.0737071);
    private GeoApiContext geoApiContext;
    private DatabaseReference mDatabase;
    private ValueEventListener locationListener;
    private Map<String, UserLocationData> activeUsers = new HashMap<>();
    private Map<String, Marker> userMarkers = new HashMap<>();
    private List<Polyline> currentRoute = new ArrayList<>();

    // Clase para almacenar datos de ubicación de usuario
    private static class UserLocationData {
        LatLng location;
        String nombre;
        Long timestamp;

        UserLocationData(LatLng location, String nombre, Long timestamp) {
            this.location = location;
            this.nombre = nombre;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        FirebaseDatabase.getInstance().setPersistenceEnabled(false);
        mDatabase = FirebaseDatabase.getInstance().getReference("ubicaciones");

        geoApiContext = new GeoApiContext.Builder()
                .apiKey("your-api-key")
                .build();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Button btnRoute = findViewById(R.id.btnRoute);
        btnRoute.setOnClickListener(v -> trazarRutaCompleta());

        limpiarDatosAntiguos();
        setupLocationListener();
    }

    private void limpiarDatosAntiguos() {
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    snapshot.getRef().removeValue();
                }
                Toast.makeText(DriverActivity.this,
                        "Datos anteriores eliminados", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error al limpiar datos: " + error.getMessage());
            }
        });
    }

    private void setupLocationListener() {
        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    procesarUbicacionUsuario(userSnapshot);
                }
                actualizarCamaraMapa();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverActivity.this,
                        "Error al obtener ubicaciones: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };
        mDatabase.addValueEventListener(locationListener);
    }

    private void procesarUbicacionUsuario(DataSnapshot userSnapshot) {
        try {
            String estado = userSnapshot.child("estado").getValue(String.class);
            Long timestamp = userSnapshot.child("timestamp").getValue(Long.class);
            String nombre = userSnapshot.child("nombre").getValue(String.class);
            Double lat = userSnapshot.child("latitud").getValue(Double.class);
            Double lng = userSnapshot.child("longitud").getValue(Double.class);

            if ("pendiente".equals(estado) && timestamp != null &&
                    lat != null && lng != null && nombre != null) {

                String userId = userSnapshot.getKey();
                LatLng location = new LatLng(lat, lng);

                // Actualizar o crear nuevo registro de usuario
                UserLocationData prevData = activeUsers.get(userId);
                if (prevData == null || timestamp > prevData.timestamp) {
                    activeUsers.put(userId, new UserLocationData(location, nombre, timestamp));
                    actualizarMarcadorUsuario(userId, location, nombre);

                    Toast.makeText(DriverActivity.this,
                            "Nueva ubicación recibida de: " + nombre,
                            Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al procesar ubicación de usuario", e);
        }
    }

    private void actualizarMarcadorUsuario(String userId, LatLng location, String nombre) {
        if (mMap == null) return;

        // Remover marcador anterior si existe
        Marker prevMarker = userMarkers.get(userId);
        if (prevMarker != null) {
            prevMarker.remove();
        }

        // Crear nuevo marcador
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(location)
                .title("Usuario: " + nombre)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        userMarkers.put(userId, marker);
    }

    private void actualizarCamaraMapa() {
        if (mMap == null || activeUsers.isEmpty()) return;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(PUNTO_INICIAL);
        builder.include(PUNTO_FINAL);

        for (UserLocationData userData : activeUsers.values()) {
            builder.include(userData.location);
        }

        LatLngBounds bounds = builder.build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }

    private void trazarRutaCompleta() {
        if (activeUsers.isEmpty()) {
            Toast.makeText(this, "No hay usuarios activos...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Limpiar rutas anteriores
        for (Polyline route : currentRoute) {
            route.remove();
        }
        currentRoute.clear();

        // Obtener todos los puntos en orden
        List<LatLng> waypoints = new ArrayList<>();
        waypoints.add(PUNTO_INICIAL);
        waypoints.addAll(getOptimizedWaypoints());
        waypoints.add(PUNTO_FINAL);

        // Iniciar el trazado de la ruta
        new RutaCompletaAsyncTask().execute(waypoints.toArray(new LatLng[0]));
    }

    private List<LatLng> getOptimizedWaypoints() {
        // Implementación simple: ordenar por distancia al punto anterior
        List<LatLng> optimizedPoints = new ArrayList<>();
        List<UserLocationData> remainingUsers = new ArrayList<>(activeUsers.values());
        LatLng currentPoint = PUNTO_INICIAL;

        while (!remainingUsers.isEmpty()) {
            int nearestIndex = 0;
            double minDistance = Double.MAX_VALUE;

            // Encontrar el punto más cercano
            for (int i = 0; i < remainingUsers.size(); i++) {
                double distance = calculateDistance(currentPoint, remainingUsers.get(i).location);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestIndex = i;
                }
            }

            // Agregar el punto más cercano a la ruta
            UserLocationData nearest = remainingUsers.remove(nearestIndex);
            optimizedPoints.add(nearest.location);
            currentPoint = nearest.location;
        }

        return optimizedPoints;
    }

    private double calculateDistance(LatLng point1, LatLng point2) {
        double lat1 = point1.latitude;
        double lon1 = point1.longitude;
        double lat2 = point2.latitude;
        double lon2 = point2.longitude;
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return dist;
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    private class RutaCompletaAsyncTask extends AsyncTask<LatLng, Void, List<DirectionsResult>> {
        @Override
        protected List<DirectionsResult> doInBackground(LatLng... waypoints) {
            try {
                List<DirectionsResult> results = new ArrayList<>();

                // Crear rutas entre cada par de puntos consecutivos
                for (int i = 0; i < waypoints.length - 1; i++) {
                    DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                            .origin(new com.google.maps.model.LatLng(
                                    waypoints[i].latitude,
                                    waypoints[i].longitude))
                            .destination(new com.google.maps.model.LatLng(
                                    waypoints[i + 1].latitude,
                                    waypoints[i + 1].longitude))
                            .await();
                    results.add(result);
                }

                return results;
            } catch (Exception e) {
                Log.e(TAG, "Error al obtener direcciones", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<DirectionsResult> results) {
            if (results != null && !results.isEmpty()) {
                // Limpiar rutas anteriores
                for (Polyline route : currentRoute) {
                    route.remove();
                }
                currentRoute.clear();
                limpiarDatosAntiguos();

                // Dibujar cada segmento de la ruta
                for (DirectionsResult result : results) {
                    Polyline polyline = dibujarRuta(result);
                    if (polyline != null) {
                        currentRoute.add(polyline);
                    }
                }
            }
        }

        private Polyline dibujarRuta(DirectionsResult result) {
            if (result.routes.length > 0) {
                DirectionsRoute route = result.routes[0];
                List<LatLng> decodedPath = new ArrayList<>();

                for (com.google.maps.model.LatLng latLng : route.overviewPolyline.decodePath()) {
                    decodedPath.add(new LatLng(latLng.lat, latLng.lng));
                }

                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(decodedPath)
                        .width(12)
                        .color(Color.BLUE)
                        .geodesic(true);

                return mMap.addPolyline(polylineOptions);
            }
            return null;
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.addMarker(new MarkerOptions()
                .position(PUNTO_INICIAL)
                .title("Punto Inicial")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        mMap.addMarker(new MarkerOptions()
                .position(PUNTO_FINAL)
                .title("Punto Final")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(PUNTO_INICIAL, 12f));
        actualizarCamaraMapa();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }
        if (locationListener != null) {
            mDatabase.removeEventListener(locationListener);
        }
    }
}