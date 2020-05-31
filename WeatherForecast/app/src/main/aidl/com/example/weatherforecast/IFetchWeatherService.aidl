// IFetchWeatherService.aidl
package com.example.weatherforecast;

// Declare any non-default types here with import statements
import com.example.weatherforecast.IFetchDataListener;

interface IFetchWeatherService {
    void retrieveWeatherData();
    void registerFetchDataListener(IFetchDataListener listener);
    void unregisterFetchDataListener(IFetchDataListener listener);
}