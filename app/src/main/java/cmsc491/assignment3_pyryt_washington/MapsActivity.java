/*
    Assignment #3 for CMSC 491
    Due Date: 4/23/2016
    Friend Finder App
    This app allows you to find friends in your area (within a 1km radius). User can create an
      account, and users within a certain radius will appear on a map.
    Filename: MapsActivity.java
        This activity displays the map of the user's location and other users in the area who have
            registered their location to be within 1 km of the user. When the activity starts, the
            user's location is updated in the database.
    Authors: Amanda Pyryt and Brooke Washington
*/


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
import android.widget.Toast;

import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    GoogleMap mMap;

    private ArrayList<String> usersLocations;
    private ArrayList<Marker> usersMarkers;

    private LocationManager locManager;
    private Location userLocation;

    //information about the current user
    private String username;
    private String name;
    LatLng currLoc;

    //the marker of the current user
    private Marker userMarker;

    //keeps track if we have already set a marker for the user - so we do not set more than one
    private boolean markerSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //gets the name and username received from the register/login screen
        //passed in with a semicolon between the two
        Intent myIntent = getIntent();
        String usernameInfo;

        //depends if we get it from RegisterActivity or LoginActivity
        try {
            usernameInfo = myIntent.getStringExtra(RegisterActivity.userInfo);
        } catch (Exception e) {
            usernameInfo = myIntent.getStringExtra(LoginActivity.userInfo);
        }
        String[] retrieved = usernameInfo.split(";");
        name = retrieved[0];
        username = retrieved[1];
    }

    /*
     * Starts the location manager when the app is resumed
     */
    @Override
    protected void onResume() {
        super.onResume();

        //starts location manager
        locManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10, 0, this);
    }


    /*
     * Turns off the location manager when the app is paused
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locManager.removeUpdates(this);
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onLocationChanged(Location location) {

        //get the current location of the user
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        userLocation = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        double user_lat = userLocation.getLatitude();
        double user_long = userLocation.getLongitude();
        currLoc = new LatLng(user_lat, user_long);

        //create a marker if we haven't already for the current user
        if (!markerSet) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(currLoc)
                    .title(name);

            //adds the marker to the map
            userMarker = mMap.addMarker(markerOptions);

            markerSet = true;

            //start the task to get other users from the database
            FindUsersTask callScript = new FindUsersTask();
            callScript.execute();
        }
        else {

            //updates the position of the user
            userMarker.setPosition(currLoc);

            //zooms the map in so they can see users within 1km
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currLoc, 16));

            //updates the location of the current user in the database
            UpdateDatabase update = new UpdateDatabase();
            update.execute();

            //updates the visibility of the other markers
            updateVisibility();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /*
     * Updates the user's entry in the database with their current location
     */
    public class UpdateDatabase extends AsyncTask<Void, Void, Boolean> {

        private String serverResponse;

        //the code that will be returned from the server upon success
        private final int SUCCESS = 5;

        @Override
        protected Boolean doInBackground(Void... params) {

            //sends messages to the server
            try {
                String findUserPhpScript = "http://mpss.csce.uark.edu/~team1/Update_location.php?";
                findUserPhpScript += "latitude=" + URLEncoder.encode(userLocation.getLatitude() + "", "UTF-8");
                findUserPhpScript += "&longitude=" + URLEncoder.encode(userLocation.getLongitude() + "", "UTF-8");
                findUserPhpScript += "&email=" + URLEncoder.encode(username, "UTF-8");
                URL url = new URL(findUserPhpScript);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                //gets the server response
                try {
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    InputStreamReader in = new InputStreamReader(conn.getInputStream());
                    BufferedReader input = new BufferedReader(in);

                    serverResponse = input.readLine();

                    //if the location was updated successfully
                    if (Integer.parseInt(serverResponse) == SUCCESS) {
                        return true;
                    }
                    else {
                        return false;
                    }
                }
                catch (Exception e) {
                    return false;
                }
                finally {
                    conn.disconnect();
                }
            }
            catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {

            super.onPostExecute(aBoolean);

            //if there was a problem updating the user's information in the database, display an
            //  error message
            if (!aBoolean) {
                Toast errorMessageToast = Toast.makeText(MapsActivity.this, "Error connecting to " +
                        "database. Please check your connection and try again", Toast.LENGTH_LONG);
                errorMessageToast.show();
            }
        }
    }

    /*
     * Retrieve users from a database who are within a certain radius of the current user
     */
    public class FindUsersTask extends AsyncTask<Void, Void, Boolean> {

        private String serverResponse;

        //the code that will be returned from the server upon success
        private final int SUCCESS = 5;

        @Override
        protected Boolean doInBackground(Void... params) {

            //execute the script to begin retrieving users
            try {
                String findUserPhpScript = "http://mpss.csce.uark.edu/~team1/Find_locations.php";
                URL url = new URL(findUserPhpScript);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                //call the script and receive back the list of users within a 1km radius of the current user
                try {
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    InputStreamReader in = new InputStreamReader(conn.getInputStream());
                    BufferedReader input = new BufferedReader(in);

                    //store the names and locations in this arrayList, so we can use them later when we get to the map activity
                    usersLocations = new ArrayList<>();

                    //keeps track of the markers of the other users
                    usersMarkers = new ArrayList<>();

                    //gets the success/error code
                    serverResponse = input.readLine();

                    //stores the users in an arrayLit
                    if (Integer.parseInt(serverResponse) == SUCCESS) {

                        String line = null;
                        while ((line = input.readLine()) != null) {
                            usersLocations.add(line);
                        }
                        return true;
                    }
                    else {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {

            //if there was a problem getting the users, display an error message
            if (!success) {
                Toast errorMessageToast = Toast.makeText(MapsActivity.this, "Error trying to connect " +
                        "to the database. Please check your connection and try again", Toast.LENGTH_LONG);
                errorMessageToast.show();
            }
            else {
                //go through each location and make a marker on the map

                //tells us the index of the user we are looking at, so we can correspond it with the marker
                //in the arrayList
                int count = 0;
                for (String s : usersLocations) {

                    //the info is received name;latitutde;longitude
                    String[] splitInfo = s.split(";");
                    LatLng usersLoc = new LatLng(Double.parseDouble(splitInfo[1]), Double.parseDouble(splitInfo[2]));
                    String usersName = splitInfo[0];

                    Location otherUserLoc = new Location("");
                    otherUserLoc.setLatitude(Double.parseDouble(splitInfo[1]));
                    otherUserLoc.setLongitude(Double.parseDouble(splitInfo[2]));

                    //adds the marker to the list of markers
                    usersMarkers.add(mMap.addMarker(new MarkerOptions().position(usersLoc).title(usersName)));
                    //set invisible until we find out how close it is to the user
                    usersMarkers.get(count).setVisible(false);

                    //calls the function that sets the visibility - the marker is visible if it is within 1km of the user
                    updateVisibility();

                    count++;
                }
            }
        }
    }


    /*
     * Updates the visibility of the markers when the user moves around
     */

    protected void updateVisibility () {

        //sets the visibility of every marker on the map
        for (int i = 0; i < usersMarkers.size(); i++) {

            //calculates the users distance and sets their visibility
            //the info is received name;latitutde;longitude
            String[] splitInfo = usersLocations.get(i).split(";");

            Location otherUserLoc = new Location("");
            otherUserLoc.setLatitude(Double.parseDouble(splitInfo[1]));
            otherUserLoc.setLongitude(Double.parseDouble(splitInfo[2]));

            float distance = userLocation.distanceTo(new Location(otherUserLoc));
            if (distance <= 1000) {
                //add a marker if the distance is within 1000m (1km)
                usersMarkers.get(i).setVisible(true);
            }
        }
    }
}