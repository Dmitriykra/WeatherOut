package dimaster.app.weatherout.Constants

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

object Constants {

    const val APP_ID: String = "dd147eb20ed12a81d17607901452fdde"
    //const val APP_ID: String = "172f17b40bd0d2237ee355a039ff2118"
    //const val APP_ID: String = "b193609ce3975a2cb599bde9fc539295"
    //const val APP_ID: String = "b6b4a92a1838e5b93fd961549c9722e9"
    const val BASE_URL: String =  "https://api.openweathermap.org/data/"
    const val PREF_NAME: String = "weather"
    const val RESP_DATA: String = "resp_data"

    fun isNetworkAvailable(context: Context) : Boolean {
        val connectivityManager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE)
        as ConnectivityManager

        //if version of phone up or equal 23
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            //if empty (network don`t exist) -> return false (?: its "if" statement)
            val network = connectivityManager.activeNetwork ?: return false

            //also check capability and return false if it null
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }
}