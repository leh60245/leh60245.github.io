package com.example.weatherforecast;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;

import com.example.weatherforecast.provider.WeatherContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Vector;

public class FetchWeatherService extends Service {
    public static final String ACTION_RETRIEVE_WEATHER_DATA = "com.loyid.weatherforecast.RETRIEVE_DATA";
    public static final String EXTRA_WEATHER_DATA = "weather-data";
    public FetchWeatherService() {
    }

    //111
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent.getAction();
        if (action.equals(ACTION_RETRIEVE_WEATHER_DATA)) {
            retrieveWeatherData(startId);
        }
        return super.onStartCommand(intent, flags, startId);
    }
    /*
    private void retrieveWeatherData(int startId) {
        FetchWeatherTask weatherTask = new FetchWeatherTask(startId);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String cityId = prefs.getString("city", "1835847");
        weatherTask.execute(cityId);
    }
    */
    //222
    @Override
    public IBinder onBind(Intent intent) {
        return (IBinder) new FetchWeatherServiceProxy(this);
    }

    //333
    public class FetchWeatherTask extends AsyncTask<String, Void, Void> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
        private int mStartId = -1;

        public FetchWeatherTask(int startId) {
            Log.d(LOG_TAG, "this is FetchWeatherTask", new RuntimeException());
            mStartId = startId;
        }

        /* The date/time conversion code is going to be moved outside the asynctask later,
         * so for convenience we're breaking it out into its own method now.
         */
        /* #9에서 사망
        private String getReadableDateString(long time){

            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("YYYY MM dd");
            return shortenedDateFormat.format(time);
        }


        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }
        */


        private void getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            Time dayTime = new Time();
            dayTime.setToNow();

            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                String day;
                String description;
                String highAndLow;

                JSONObject dayForecast = weatherArray.getJSONObject(i);

                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay + i);


                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                ContentValues values = new ContentValues();
                values.put(WeatherContract.WeatherColumns.COLUMN_DATE, dateTime);
                values.put(WeatherContract.WeatherColumns.COLUMN_SHORT_DESC, description);
                values.put(WeatherContract.WeatherColumns.COLUMN_MIN_TEMP, low);
                values.put(WeatherContract.WeatherColumns.COLUMN_MAX_TEMP, high);
                cVVector.add(values);
                if (cVVector.size() > 0) {
                    Log.d("AAAAAA", "OH YEA");
                    ContentValues[] cvArray = new ContentValues[cVVector.size()];
                    cVVector.toArray(cvArray);
                    getContentResolver().bulkInsert(WeatherContract.WeatherColumns.CONTENT_URI, cvArray);

                    // delete old data so we don't build up an endless history
                    getContentResolver().delete(WeatherContract.WeatherColumns.CONTENT_URI,
                            WeatherContract.WeatherColumns.COLUMN_DATE + " <= ?",
                            new String[]{Long.toString(dayTime.setJulianDay(julianStartDay - 1))});
                    Log.d("AAAAAA", "OH YEA");
                }
            }
        }

        @Override
        protected Void doInBackground(String... params) {

            // If there's no zip code, there's nothing to look up.  Verify size of params.
            if (params.length == 0) {
                return null;
            }

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            String format = "json";
            String units = "metric";
            int numDays = 14;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "id";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID_PARAM = "APPID";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APPID_PARAM, "5fd2f2cde90c1533efb95b19c048a528")
                        .build();

                URL url = new URL(builtUri.toString());

                Log.v(LOG_TAG, "Built URI " + builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();

                Log.v(LOG_TAG, "Forecast string: " + forecastJsonStr);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }

            // This will only happen if there was an error getting or parsing the forecast.
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            stopSelf(mStartId);
        }
    }

    //444
    //#8부터 새 단장장
   private void notifyWeatherDataRetrieved(String[] result) {
        synchronized (mListeners) {
            for (IFetchDataListener listener : mListeners) {
                try {
                    listener.onWeatherDataRetrieved(result);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        }
        Intent intent = new Intent(ACTION_RETRIEVE_WEATHER_DATA);
        intent.putExtra(EXTRA_WEATHER_DATA, result);
        sendBroadcast(intent);
    }
    private void retrieveWeatherData(int startId) {
        FetchWeatherTask weatherTask = new FetchWeatherTask(startId);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String cityId = prefs.getString("city", "1835847");
        weatherTask.execute(cityId);
    }

    private ArrayList<IFetchDataListener> mListeners = new ArrayList<IFetchDataListener>();
    private void registerFetchDataListener(IFetchDataListener listener) {
        synchronized (mListeners) {
            if (mListeners.contains(listener)) {
                return;
            }
            mListeners.add(listener);
        }
    }

    private void unregisterFetchDataListener(IFetchDataListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                return;
            }

            mListeners.remove(listener);
        }
    }

    private class FetchWeatherServiceProxy extends IFetchWeatherService.Stub {
        private WeakReference<FetchWeatherService> mService = null;

        public FetchWeatherServiceProxy(FetchWeatherService service) {
            mService = new WeakReference<FetchWeatherService>(service);
        }

        @Override
        public void retrieveWeatherData() throws RemoteException {
            mService.get().retrieveWeatherData(-1);
        }

        @Override
        public void registerFetchDataListener(IFetchDataListener listener) throws RemoteException {
            mService.get().registerFetchDataListener(listener);
        }

        @Override
        public void unregisterFetchDataListener(IFetchDataListener listener) throws RemoteException {
            mService.get().unregisterFetchDataListener(listener);
        }
    }
}
