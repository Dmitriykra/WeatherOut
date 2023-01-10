package dimaster.app.weatherout

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.location.LocationManagerCompat.isLocationEnabled
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import dimaster.app.weatherout.Constants.Constants
import dimaster.app.weatherout.Models.WeatherResponse
import dimaster.app.weatherout.Network.WeatherInterface
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var fusLocProCl: FusedLocationProviderClient
    private var progressDialog: Dialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusLocProCl = LocationServices.getFusedLocationProviderClient(this)

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

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val locationRequest = com.google.android.gms.location.LocationRequest()
        locationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY

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

            //show dialog  before enqueue
            showCustomDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>

                ) {
                    if(response.isSuccessful){

                        hideDialog()

                        val weatherList: WeatherResponse? = response.body()
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
}