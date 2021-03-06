package dickshern.android_car_booking;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import dickshern.android_car_booking.UserProfile.helper.PrefsManager;
import dickshern.android_car_booking.global.Helpers;
import dickshern.android_car_booking.http.HttpHandler;
import dickshern.android_car_booking.http.HttpResponse;

import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_ARR_DROPOFFLOCATIONS;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_ARR_LOCATION;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_AVAILABLECARS;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_DATA;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_END_TIME;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_ERROR;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_ID;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_MESSAGE;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_START_TIME;
import static dickshern.android_car_booking.database.DatabaseConfig.WEBTAG_SUCCESS;
import static dickshern.android_car_booking.database.DatabaseConfig.webEPBookingAvailability;

/**
 * Created by dickshern on 09-Sept-18.
 */

public class AllAvailableBookingsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener{

    ListView lv;

    // JSON Node names
    public static final String TAG_BOOKING_ALL_DETAILS = "booking_all_details";

    PrefsManager prefsManager;

    private SwipeRefreshLayout swipeRefreshLayout;

    ProgressDialog pDialog;
    HashMap<String, String> jsonMap = new HashMap<>();
    HashMap<String, String> tempMessageMap = new HashMap<>();
    String tempMessage = "";

    ArrayList<HashMap<String, String>> listItems;

    //JSON node names
    public static final String TAG_ID = "id";
    public static final String TAG_ARR_LOCATION = "location";
    public static final String TAG_AVAILABLECARS= "available_cars";
    public static final String TAG_ARR_DROPOFFLOCATIONS = "dropoff_locations";
    public static final String TAG_LOCATION_ADDRESS = "location_address";
    public static final String TAG_DROPOFFLOCATIONS_COUNT = "dropoff_locations_count";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Helpers.setFullScreen(this);
        setContentView(R.layout.database_list_view_main);

        prefsManager = new PrefsManager(AllAvailableBookingsActivity.this);

        ImageButton imgBtnBack = findViewById(R.id.imgBtnBack);
        imgBtnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView tvDBCategory = (TextView) findViewById(R.id.tvDBCategory);
        tvDBCategory.setText("All Available Bookings");
        FloatingActionButton fabTest = (FloatingActionButton) findViewById(R.id.fab1);
        fabTest.hide();

        // Get listview
        lv = findViewById(R.id.listViewMain);

        // on item select, view further details of item
        lv.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // Starting new intent
                Intent in = new Intent(getApplicationContext(),
                        ViewAvailableBookingDetailsActivity.class);
                // Send item to next activity
                in.putExtra(TAG_BOOKING_ALL_DETAILS, listItems.get(position));

                // starting new activity and expecting some response back
                startActivityForResult(in, 100);
            }
        });


        ViewCompat.setNestedScrollingEnabled(lv, true);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);

        /**
         * Showing Swipe Refresh animation on activity create
         * As animation won't start on onCreate, post runnable is used
         */
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });

        FloatingActionButton fab = findViewById(R.id.fabRefreshList);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refresh();
            }
        });

        //Add list header
        LinearLayout layoutHeader = findViewById(R.id.layoutHeader);
        LayoutInflater tableHead = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newRow = tableHead.inflate(R.layout.database_list_booking_availability, null);

        ((TextView) newRow.findViewById(R.id.listTVID)).setText(R.string.col_id);
        Helpers.adjustLayoutWeight((TextView) newRow.findViewById(R.id.listTVID), 0.5f);
        ((TextView) newRow.findViewById(R.id.listTVAddress)).setText(R.string.col_address);
        Helpers.adjustLayoutWeight((TextView) newRow.findViewById(R.id.listTVAddress), 1.0f);
        newRow.findViewById(R.id.listTVAddress).setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ((TextView) newRow.findViewById(R.id.listTVAvailableCars)).setText(R.string.col_available_cars);
        Helpers.adjustLayoutWeight((TextView) newRow.findViewById(R.id.listTVAvailableCars), 2.0f);
        newRow.findViewById(R.id.listTVAvailableCars).setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
//        ((TextView) newRow.findViewById(R.id.listTVDropOffLocations)).setText(R.string.col_dropoff_locations);
//        Helpers.adjustLayoutWeight((TextView) newRow.findViewById(R.id.listTVDropOffLocations), 2.0f);
        layoutHeader.addView(newRow);

    }


    @Override
    public void onRefresh() {
        refresh();
    }

    public void reset() {
        jsonMap = new HashMap<>();
        tempMessage = "";
        listItems = new ArrayList<HashMap<String, String>>();
    }


    private class webGetBooking extends AsyncTask<Void, Void, HashMap<String, String>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Showing progress dialog
            pDialog = new ProgressDialog(AllAvailableBookingsActivity.this);
            pDialog.setMessage(getString(R.string.loading_custom, "Retrieving available bookings"));
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();

            reset();
        }

        @Override
        protected HashMap<String, String> doInBackground(Void... arg0) {
            HttpHandler httpHandler = new HttpHandler(AllAvailableBookingsActivity.this);
            HashMap<String, String> params = new HashMap<>();

            long startTime = System.currentTimeMillis() / 1000;
            long endtime = (System.currentTimeMillis() / 1000) + 3600;


            Log.e("@@@START TIME", String.valueOf(startTime));
            Log.e("@@@END  TIME", String.valueOf(endtime));
            params.put(WEBTAG_START_TIME, String.valueOf(startTime));
            params.put(WEBTAG_END_TIME, String.valueOf(endtime));

            JSONObject jsonParam = new JSONObject();
            try {
                jsonParam.put(WEBTAG_START_TIME, String.valueOf(startTime));
                jsonParam.put(WEBTAG_END_TIME, String.valueOf(endtime));

            } catch (JSONException e) {
                e.printStackTrace();
            }

            HttpResponse response = httpHandler.makeHttpRequest(webEPBookingAvailability, "GET", jsonParam, false);
            String jsonStr = response.getResponse();

            if (jsonStr != null) {
                try {

                    switch (response.getHTTPCode()) {
                        case HttpsURLConnection.HTTP_OK:

                            JSONObject jsonobj = new JSONObject(jsonStr);
                            JSONArray jsonArr = jsonobj.getJSONArray(WEBTAG_DATA);

                            // looping through all Available Booking
                            for (int i = 0; i < jsonArr.length(); i++) {
                                JSONObject c = jsonArr.getJSONObject(i);

                                // creating new HashMap
                                HashMap<String, String> map = new HashMap<String, String>();

                                map.put(TAG_ID, c.getString(WEBTAG_ID));
                                map.put(TAG_ARR_LOCATION, c.getString(WEBTAG_ARR_LOCATION));
                                map.put(TAG_AVAILABLECARS, c.getString(WEBTAG_AVAILABLECARS));
                                map.put(TAG_ARR_DROPOFFLOCATIONS, c.getString(WEBTAG_ARR_DROPOFFLOCATIONS));

                                String[] location = new String[0];
                                try {
                                    location = Helpers.stringToStrArray(c.getString(WEBTAG_ARR_LOCATION));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                map.put(TAG_LOCATION_ADDRESS, Helpers.getCompleteAddress(
                                        AllAvailableBookingsActivity.this, Double.valueOf(location[0]), Double.valueOf(location[1])));
                                map.put(TAG_DROPOFFLOCATIONS_COUNT, String.valueOf(c.getJSONArray(WEBTAG_ARR_DROPOFFLOCATIONS).length()));

                                // adding HashList to ArrayList
                                listItems.add(map);
                            }

                            tempMessageMap.put(WEBTAG_SUCCESS, tempMessage);
                            return tempMessageMap;
                        default:
                            jsonobj = new JSONObject(jsonStr);
                            tempMessage += jsonobj.getString(WEBTAG_MESSAGE);

                            tempMessageMap.put(WEBTAG_MESSAGE, tempMessage);
                            return tempMessageMap;
                    }
                } catch (final JSONException e) {
                    Log.e("@@@DEBUG", getString(R.string.system_message_error_parsing_json) + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.system_message_error_parsing_json) + e.getMessage(),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

                }
            } else {
                Log.e("@@@DEBUG", getString(R.string.system_message_fail_get_json_server));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.system_message_fail_get_json_server),
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            return null;
        }

        @Override
        protected void onPostExecute(HashMap<String, String> response) {
            // dismiss the dialog once done
            if (pDialog.isShowing())
                pDialog.dismiss();
            String key = "";
            String value = "";

            if (response != null) {
                for (Map.Entry<String, String> entry : response.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();
                }

                if (key.equals(WEBTAG_SUCCESS)) {
                    //updating UI from Background Thread
                    runOnUiThread(new Runnable() {
                        public void run() {
                            /**
                             * Updating parsed JSON data into ListView
                             * */
                            ListAdapter adapter = new SimpleAdapter(
                                    AllAvailableBookingsActivity.this, listItems,
                                    R.layout.database_list_booking_availability, new String[]{TAG_ID,
                                    TAG_LOCATION_ADDRESS, TAG_AVAILABLECARS, TAG_DROPOFFLOCATIONS_COUNT},
                                    new int[]{R.id.listTVID, R.id.listTVAddress, R.id.listTVAvailableCars, R.id.listTVDropOffLocations});
                            //updating listview
                            lv.setAdapter(adapter);
                        }
                    });
                } else{
                    Helpers.replaceToast(AllAvailableBookingsActivity.this, value, Toast.LENGTH_SHORT);

                    //updating UI from Background Thread
                    runOnUiThread(new Runnable() {
                        public void run() {
                            /**
                             * Updating parsed JSON data into ListView
                             * */
                            ListAdapter adapter = new SimpleAdapter(
                                    AllAvailableBookingsActivity.this, listItems,
                                    R.layout.database_list_error, new String[]{WEBTAG_ERROR},
                                    new int[]{R.id.tvError});
                            //updating listview
                            lv.setAdapter(adapter);
                        }
                    });
                }
            } else
                Helpers.replaceToast(AllAvailableBookingsActivity.this, getString(R.string.custom_message_error, getString(R.string.system_response_null)), Toast.LENGTH_SHORT);
        }
    }

    public void refresh(){
        swipeRefreshLayout.setRefreshing(true);
        Helpers.showToast(AllAvailableBookingsActivity.this, getString(R.string.system_getting_custom, "Available Bookings"), Toast.LENGTH_SHORT);
        new webGetBooking().execute();
        Helpers.DelayedRefreshStop(swipeRefreshLayout);
    }


}
