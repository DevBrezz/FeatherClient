package com.example.bedrockclient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Waypoint(val name: String, val x: Double, val y: Double, val z: Double)

object WaypointStore {
    private fun p(c: Context) = c.getSharedPreferences("feather_waypoints", Context.MODE_PRIVATE)
    fun list(c: Context): List<Waypoint> = try {
        val a = JSONArray(p(c).getString("list", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Waypoint(o.getString("n"), o.getDouble("x"), o.getDouble("y"), o.getDouble("z")) }
    } catch (_: Exception) { emptyList() }
    private fun save(c: Context, l: List<Waypoint>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("n", it.name).put("x", it.x).put("y", it.y).put("z", it.z)) }
        p(c).edit().putString("list", a.toString()).apply()
    }
    fun add(c: Context, w: Waypoint) = save(c, list(c) + w)
    fun remove(c: Context, index: Int) = save(c, list(c).toMutableList().also { if (index in it.indices) it.removeAt(index) })
}
