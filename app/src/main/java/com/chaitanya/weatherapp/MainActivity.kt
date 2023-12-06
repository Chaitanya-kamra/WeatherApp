package com.chaitanya.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.chaitanya.weatherapp.databinding.ActivityMainBinding
import com.chaitanya.weatherapp.models.WeatherResponse
import com.chaitanya.weatherapp.network.WeatherService
import com.chaitanya.weatherapp.utils.Constants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding

    private lateinit var saveData: SaveData


    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0

    private var requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
                permissions ->
            permissions.entries.forEach {
                val permission = it.key
                val isGranted = it.value
                if (isGranted){
                    when (permission) {
                        Manifest.permission.ACCESS_FINE_LOCATION -> {
                            setLocation()
                        }
                    }
                }
                else{
                    if (permission == Manifest.permission.ACCESS_FINE_LOCATION){
                        Toast.makeText(this,"Provide Fine location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun setLocation() {
        showProgressDialog()
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(100)
            .setMaxUpdateDelayMillis(500)
            .setMaxUpdates(1)
            .build()


        mFusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation

            if (lastLocation != null) {
                mLatitude = lastLocation.latitude
            }
            if (lastLocation != null) {
                mLongitude = lastLocation.longitude
            }
            Toast.makeText(this@MainActivity,mLatitude.toString()+mLongitude.toString(),Toast.LENGTH_SHORT).show()

            getLocationWeatherDetails()
        }
    }


    private fun showProgressDialog() {
        runOnUiThread {
            binding.lnlProgress.visibility = View.VISIBLE
            binding.lnlDetails.visibility = View.GONE
        }

    }
    private fun dismissProgressDialog() {
        runOnUiThread {
            binding.lnlProgress.visibility = View.GONE
            binding.lnlDetails.visibility = View.VISIBLE
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)

        saveData = SaveData(this)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

//        setUpUI()
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

//            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
//            startActivity(intent)
        } else {
            requestCurrentLocation()
        }

    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

     override fun onOptionsItemSelected(item: MenuItem): Boolean {
         when (item.itemId) {
             R.id.action_refresh -> {
                 if (!isLocationEnabled()) {
                     Toast.makeText(
                         this,
                         "Your location provider is turned off. Please turn it on.",
                         Toast.LENGTH_SHORT
                     ).show()

//            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
//            startActivity(intent)
                 } else {
                     requestCurrentLocation()
                 }
                 return true
             }

         }
         return super.onOptionsItemSelected(item)
     }

    private fun isLocationEnabled(): Boolean {
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)

    }

    private  fun requestCurrentLocation(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.CAMERA)){
            showRationalDialogForPermissions()
        } else {
            requestPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }
    private fun getLocationWeatherDetails() {

        if (Constants.isNetworkAvailable(this@MainActivity)) {

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()


            val service: WeatherService =
                retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                mLatitude, mLongitude, Constants.METRIC_UNIT, Constants.APP_ID
            )


            listCall.enqueue(object : Callback<WeatherResponse> {
           override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {

                        dismissProgressDialog()

                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        CoroutineScope(Dispatchers.IO).launch {
                            saveData.storeInfo(weatherResponseJsonString)
                        }

                        setUpUI()

                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    dismissProgressDialog()
                    Toast.makeText(this@MainActivity,"Failed to get Result",Toast.LENGTH_LONG).show()

                }
            })
        } else {
            dismissProgressDialog()
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun setUpUI() {


        CoroutineScope(Dispatchers.IO).launch {
                saveData.dataFlow.collect{
                    var weatherResponseJsonString  = it
                    Log.e("fafa",weatherResponseJsonString.toString())
                    val weatherList =
                        Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
                    withContext(Dispatchers.Main) {
                        if (weatherList!= null){
                            for (z in weatherList.weather.indices) {

                                binding.apply {
                                    tvMain.text = weatherList.weather[z].main
                                    tvMainDescription.text = weatherList.weather[z].description
                                    tvTemp.text =
                                        weatherList.main.temp.toString() + "°C"
                                    tvHumidity.text = weatherList.main.humidity.toString() + " percent"
                                    tvMin.text = weatherList.main.tempMin.toString() + " min"
                                    tvMax.text = weatherList.main.tempMax.toString() + " max"
                                    tvSpeed.text = weatherList.wind.speed.toString()
                                    tvName.text = weatherList.name
                                    tvCountry.text = weatherList.sys.country
                                    tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong())
                                    tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong())
                                }


                                // Here we update the main icon
                                when (weatherList.weather[z].icon) {

                                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                                }
                            }
                        }

                    }
                }
            }


    }
    @SuppressLint("SimpleDateFormat")
    private fun unixTime(time: Long): String? {
        val date = Date(time*1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }



}