package dimaster.app.weatherout.Network

import dimaster.app.weatherout.Models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherInterface {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat")
        lat: Double,

        @Query("lon")
        lon: Double,

        @Query("cnt")
        cnt: Int,

        @Query("appid")
        appid: String?
    ): Call<WeatherResponse>
}