package com.chaitanya.weatherapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.datastore.preferences.core.stringPreferencesKey

object Constants {

    const val APP_ID: String = "33346f9eeb45f500d8ef3ef6544f730e"
    const val BASE_URL: String = "http://api.openweathermap.org/data/"
    const val METRIC_UNIT: String = "metric"

    const val PREFERENCE_NAME = "WeatherAppPreference"
    val WEATHER_RESPONSE_DATA = stringPreferencesKey("WEATHER_RESPONSE_DATA")



    fun isNetworkAvailable(context: Context): Boolean {
        // It answers the queries about the state of network connectivity.
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network      = connectivityManager.activeNetwork ?: return false
        val activeNetWork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetWork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetWork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetWork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}