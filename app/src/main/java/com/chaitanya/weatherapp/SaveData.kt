package com.chaitanya.weatherapp

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.chaitanya.weatherapp.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class SaveData(private val context: Context) {

    companion object{
        val Context.dataStore :DataStore<Preferences> by preferencesDataStore(name = Constants.PREFERENCE_NAME)
    }
    val dataFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Constants.WEATHER_RESPONSE_DATA] ?: ""
    }

//
//    val read : Flow<String> = context.dataStore.data
//        .catch { exception ->
//            if(exception is IOException){
//                Log.d("Datastore " , exception.message.toString())
//            }else{
//                throw exception
//            }
//        }
//        .map {
//            val name = it[Constants.WEATHER_RESPONSE_DATA] ?: ""
//            name
//        }



    suspend fun storeInfo(data: String) {
        context.dataStore.edit {
            it[Constants.WEATHER_RESPONSE_DATA] = data
        }
    }
}