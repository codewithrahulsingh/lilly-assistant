package com.rahul.lilly

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

/**
 * Mausam — Open-Meteo (free, bina key). Jagah: phone ki aakhri location, warna aapka bataya shehar.
 * Network call hai: background thread se bulao.
 */
object Weather {

    data class Report(
        val place: String, val nowTemp: Int, val feels: Int, val code: Int,
        val todayMax: Int, val todayMin: Int, val todayRain: Int,
        val tomMax: Int, val tomMin: Int, val tomRain: Int, val tomCode: Int
    )

    class NeedLocation : Exception()

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 7000
            c.readTimeout = 9000
            c.setRequestProperty("User-Agent", "Lilly-app")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** lat, lon, jagah ka naam */
    private fun where(ctx: Context): Triple<Double, Double, String> {
        val prefs = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null } catch (e: Exception) { null } }
                .maxByOrNull { it.time }
            if (loc != null) {
                @Suppress("DEPRECATION")
                val name = try {
                    Geocoder(ctx, Locale.ENGLISH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                        ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                } catch (e: Exception) { null }
                return Triple(loc.latitude, loc.longitude, name ?: prefs.getString("city", "") ?: "")
            }
        }
        val city = prefs.getString("city", "")?.trim().orEmpty()
        if (city.isBlank()) throw NeedLocation()
        val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8")))
        val r = g.optJSONArray("results")?.optJSONObject(0) ?: throw NeedLocation()
        return Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
    }

    fun fetch(ctx: Context): Report {
        val (lat, lon, place) = where(ctx)
        val j = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                    "&timezone=auto&forecast_days=2"
            )
        )
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        fun di(key: String, i: Int) = d.optJSONArray(key)?.optDouble(i, 0.0)?.let { Math.round(it).toInt() } ?: 0
        return Report(
            place, Math.round(cur.optDouble("temperature_2m")).toInt(), Math.round(cur.optDouble("apparent_temperature")).toInt(),
            cur.optInt("weather_code"),
            di("temperature_2m_max", 0), di("temperature_2m_min", 0), di("precipitation_probability_max", 0),
            di("temperature_2m_max", 1), di("temperature_2m_min", 1), di("precipitation_probability_max", 1),
            di("weather_code", 1)
        )
    }

    fun describe(code: Int, hi: Boolean): String = when (code) {
        0 -> if (hi) "आसमान साफ़ है" else "clear skies"
        1, 2 -> if (hi) "हल्के बादल हैं" else "partly cloudy"
        3 -> if (hi) "बादल छाए हैं" else "cloudy"
        45, 48 -> if (hi) "कोहरा है" else "foggy"
        in 51..57 -> if (hi) "बूंदाबांदी है" else "drizzle"
        in 61..67 -> if (hi) "बारिश है" else "rain"
        in 71..77 -> if (hi) "बर्फ़ गिर रही है" else "snow"
        in 80..82 -> if (hi) "बौछारें हैं" else "showers"
        in 95..99 -> if (hi) "आंधी-तूफ़ान है" else "thunderstorms"
        else -> if (hi) "मौसम सामान्य है" else "mixed weather"
    }

    /** Bolne layak line: aaj ya kal */
    fun line(r: Report, day: String, hi: Boolean): String {
        val at = if (r.place.isNotBlank()) (if (hi) "${r.place} में" else "in ${r.place}") else ""
        return if (day == "tomorrow") {
            if (hi) "कल $at ${describe(r.tomCode, true)}, ${r.tomMin} से ${r.tomMax} डिग्री, बारिश की संभावना ${r.tomRain} प्रतिशत।"
            else "Tomorrow $at: ${describe(r.tomCode, false)}, ${r.tomMin} to ${r.tomMax} degrees, ${r.tomRain}% chance of rain."
        } else {
            if (hi) "अभी $at ${r.nowTemp} डिग्री है, ${describe(r.code, true)}। आज ${r.todayMin} से ${r.todayMax} डिग्री, बारिश की संभावना ${r.todayRain} प्रतिशत।"
            else "It's ${r.nowTemp} degrees $at right now, ${describe(r.code, false)}. Today ${r.todayMin} to ${r.todayMax} degrees, ${r.todayRain}% chance of rain."
        }.replace(Regex("\\s+"), " ").replace(" ,", ",").replace(" :", ":")
    }
}
