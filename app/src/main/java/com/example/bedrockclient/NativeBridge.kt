package com.example.bedrockclient

object NativeBridge {
    init { System.loadLibrary("clientmod") }
    external fun init(config: String, mcVersionCode: Long, patternsPath: String): Boolean
    external fun setModule(id: String, enabled: Boolean)
    external fun version(): String
    external fun hookStatus(): String            // waiting | ok:fov,player_tick | no-patterns-file | no-pattern | error:...
    external fun scanPattern(pattern: String): String
    external fun getCoords(): FloatArray?
    external fun setZoomHeld(held: Boolean)
    external fun setZoomLevel(level: Float)      // 0.05..1.0 (pengali FOV saat zoom)
}
