package com.organism.liquidclock

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class LiquidGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = LiquidRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                renderer.touchX = event.x
                renderer.touchY = event.y
                renderer.isTouching = 1.0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                renderer.isTouching = 0.0f
            }
        }
        return true
    }
}
