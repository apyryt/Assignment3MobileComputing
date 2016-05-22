package cmsc491.assignment3_pyryt_washington;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private ArrayList<String> usersLocations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    @Override
    protected void onStart() {
        super.onStart();

        //when the application starts, start the task to get users from the database
        FindUsersTask callScript = new FindUsersTask();
        callScript.execute();
    }

    /*
     * Retrieve users from a database who are within a certain radius of the current user
     */
    public class FindUsersTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {

            //execute the script to begin retrieving users
            try {
                String findUserPhpScript = "http://mpss.csce.uark.edu/~team1/get_users.php";
                URL url = new URL(findUserPhpScript);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                //call the script and receive back the list of users within a 1km radius of the current user
                try {
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    InputStreamReader in = new InputStreamReader(conn.getInputStream());
                    BufferedReader input = new BufferedReader(in);

                    //todo: update with storing the user's name and location somehow

                    //store the names and locations in this arrayList, so we can use them later when we get to the map activity
                    usersLocations = new ArrayList<String>();

                    String line = null;
                    while ((line = input.readLine()) != null) {
                        usersLocations.add(line);
                    }
                } catch (Exception e) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean v) {

            //todo update once we know the structure of how the info is returned from php script

            //go through each location and make a marker on the map
            for (String s : usersLocations) {
                // Add a marker
                LatLng userLocation = new LatLng(12, 12);
                String usersName = "name";
                mMap.addMarker(new MarkerOptions().position(userLocation).title(usersName));
            }
        }
    }
}
