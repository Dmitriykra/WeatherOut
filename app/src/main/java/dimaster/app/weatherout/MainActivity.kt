package dimaster.app.weatherout

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import dimaster.app.weatherout.Constants.Constants
import dimaster.app.weatherout.Models.WeatherResponse
import dimaster.app.weatherout.Network.WeatherInterface
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var fusLocProCl: FusedLocationProviderClient
    private var progressDialog: Dialog? = null
    private lateinit var sharedPref: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

        fusLocProCl = LocationServices.getFusedLocationProviderClient(this)

        setupUi()

        if(weather_tv?.text?.length!! <1){
            showCustomDialog()
        }
        if(!isLocationEnabled()){
            Toast.makeText(this, "Please, turn on your GPS", Toast.LENGTH_SHORT).show()
            //run request on GPS
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ).withListener(object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){

                            Log.d("TAG", "onPermissionsChecked: done")
                            requestLocationData()
                        }

                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "You denied all permissions", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showAlertDialogForPermission()
                    }

                }).onSameThread()
                .check()
        }
    }

    private fun requestLocationData(){
        /*val locationRequest = com.google.android.gms.location.LocationRequest()
        locationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY

        fusLocProCl.requestLocationUpdates(locationRequest, locationCallback,
        Looper.myLooper())*/

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdateDelayMillis(1000)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusLocProCl.requestLocationUpdates(locationRequest, locationCallback,
            Looper.myLooper())
    }

    private val locationCallback = object  : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location? = locationResult.lastLocation
            val latitude = lastLocation?.latitude
            val longitude = lastLocation?.longitude

            Log.d("TAG", "latitude: $latitude and longitude $longitude")

            if (latitude != null && longitude != null) {
                getLocationWeatherDet(latitude, longitude)
            }
        }
    }

    private fun getLocationWeatherDet(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this@MainActivity)){
            //Init of retrofit
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            //preparing the services
            val service: WeatherInterface = retrofit
                .create(WeatherInterface::class.java)

            //prepearing listCall
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,
                longitude,
                Constants.APP_ID
            )

            Log.d("TAG", "getLocationWeatherDet: $listCall")

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>

                ) {
                    if(response.isSuccessful){

                        hideDialog()

                        val weatherList: WeatherResponse? = response.body()
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = sharedPref.edit()
                        editor.putString(Constants.RESP_DATA, weatherResponseJsonString)
                        editor.apply()
                        if (weatherList != null) {
                            setupUi()
                        }
                        Log.i("TAG", "onResponse result: $weatherList")
                    } else {

                        hideDialog()

                        val rc = response.code()

                        Log.d("TAG", "onResponse: "+response.message())

                        when(rc){
                            400 ->{
                                Log.e("TAG", "Error 400")
                            } 404 ->{
                                Log.e("TAG", "Error 404")
                            } else ->{
                                Log.e("TAG", "Error  ${response.code()}")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {

                    hideDialog()

                    Log.e("TAG", "Big error $t")
                }

            })

        } else {
            Toast.makeText(this@MainActivity, "No internet is available", Toast.LENGTH_SHORT).show()

            hideDialog()
        }
    }

    //Show Alert Dialog
    private fun showAlertDialogForPermission(){
        AlertDialog.Builder(this@MainActivity)
            .setMessage("Turn on permission")
            .setPositiveButton("Go to settings") {_, _ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException){
                e.printStackTrace()

            }
        }.setNegativeButton("Cancel"){
            dialog,
                    _->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled():Boolean{
        //get access to the system location service
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun  showCustomDialog(){
        progressDialog = Dialog(this)
        progressDialog!!.setContentView(R.layout.custom_progress_dialog)
        progressDialog!!.show()
    }

    private fun hideDialog(){
        if(progressDialog != null){
            progressDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUi(){
        val weatherResponseJsonString = sharedPref.getString(Constants.RESP_DATA, "")
        if(!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                Log.d("TAG", "setupUi: ${weatherList.weather.toString()}")
                weather_tv?.text = weatherList.weather[i].main
                condition_tv?.text = weatherList.weather[i].description
                degree_tv?.text = weatherList.main.temp.toString() +
                        //check user phone localation
                        getUnit(application.resources.configuration.locales.toString())
                sunrise_tv?.text = unixTime(weatherList.sys.sunrise)
                sunset_tv?.text = unixTime(weatherList.sys.sunset)
                wind_tv?.text = weatherList.wind.deg.toString()
                miles_per_hour?.text = weatherList.wind.speed.toString()
                percent_tv?.text = weatherList.main.grnd_level.toString()
                minimum_temp?.text = weatherList.main.temp_min.toString()
                max_temp?.text = weatherList.main.temp_max.toString()
                name_tv?.text = weatherList.name
                country_tv?.text = weatherList.sys.country

                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main?.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main?.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main?.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main?.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main?.setImageResource(R.drawable.rain)
                    "11d" -> iv_main?.setImageResource(R.drawable.storm)
                    "13d" -> iv_main?.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main?.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main?.setImageResource(R.drawable.rain)
                    "13n" -> iv_main?.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }
    private fun getUnit(value: String): String?{
        var value = "C"
        if ("US" == value){
            value = "F"
        }
        return value
    }

    private fun unixTime (timex: Long): String?{
        val date =  Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale("UK"))
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.refresh ->{
                requestLocationData()
                true
            } else -> return super.onOptionsItemSelected(item)
        }
    }



}