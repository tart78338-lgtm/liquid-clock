package com.organism.liquidclock

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Calendar
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class LiquidRenderer : GLSurfaceView.Renderer {

    private val vertexShaderSource = """
        attribute vec4 a_Position;
        void main() {
            gl_Position = a_Position;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        precision highp float;
        uniform vec2 u_Resolution;
        uniform float u_Time;
        uniform vec4 u_Clock;
        uniform vec3 u_Touch;

        float smin(float a, float b, float k) {
            float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
            return mix(b, a, h) - k * h * (1.0 - h);
        }

        float organismSDF(vec2 p) {
            float pulse = sin(u_Time * 1.8) * 0.02;
            float nucleus = length(p) - (0.24 + pulse);

            float h_ang = (u_Clock.x + u_Clock.y / 60.0) * 0.52359877 - 1.5707963;
            float m_ang = (u_Clock.y + u_Clock.z / 60.0) * 0.10471975 - 1.5707963;
            float s_ang = (u_Clock.z + u_Clock.w / 1000.0) * 0.10471975 - 1.5707963;

            vec2 h_pos = vec2(cos(h_ang), -sin(h_ang)) * 0.32;
            float d_hour = length(p - h_pos) - 0.085;

            vec2 m_pos = vec2(cos(m_ang), -sin(m_ang)) * 0.48;
            float d_min = length(p - m_pos) - 0.065;

            vec2 s_pos = vec2(cos(s_ang), -sin(s_ang)) * 0.62;
            float d_sec = length(p - s_pos) - 0.045;

            vec2 c1 = vec2(sin(u_Time * 1.1) * 0.13, cos(u_Time * 1.4) * 0.13);
            float d = smin(nucleus, length(p - c1) - 0.12, 0.18);

            d = smin(d, d_hour, 0.20);
            d = smin(d, d_min, 0.18);
            d = smin(d, d_sec, 0.14);

            if (u_Touch.z > 0.5) {
                float dt = length(p - u_Touch.xy);
                d += sin(dt * 25.0 - u_Time * 8.0) * 0.025 * smoothstep(0.5, 0.0, dt);
            }
            return d;
        }

        vec2 computeNormal(vec2 p) {
            float eps = 0.004;
            float dx = organismSDF(vec2(p.x + eps, p.y)) - organismSDF(vec2(p.x - eps, p.y));
            float dy = organismSDF(vec2(p.x, p.y + eps)) - organismSDF(vec2(p.x, p.y - eps));
            return normalize(vec2(dx, dy));
        }

        void main() {
            vec2 uv = (gl_FragCoord.xy - 0.5 * u_Resolution.xy) / min(u_Resolution.x, u_Resolution.y);
            vec3 bgColor = mix(vec3(0.01, 0.03, 0.06), vec3(0.0, 0.01, 0.02), length(uv));

            float dist = organismSDF(uv);
            vec3 finalColor = bgColor;

            if (dist < 0.05) {
                vec2 n2D = computeNormal(uv);
                vec3 normal = normalize(vec3(n2D, 0.55));
                vec3 lightDir = normalize(vec3(-0.4, 0.6, 0.75));

                float r = mix(0.02, 0.85, smoothstep(0.0, -0.2, organismSDF(uv - n2D * 0.03)));
                float g = mix(0.10, 0.95, smoothstep(0.0, -0.2, organismSDF(uv - n2D * 0.02)));
                float b = mix(0.20, 1.00, smoothstep(0.0, -0.2, organismSDF(uv - n2D * 0.01)));
                vec3 glassBody = vec3(r * 0.15, g * 0.55, b * 0.85);

                float spec = pow(max(dot(normal, normalize(lightDir + vec3(0.0, 0.0, 1.0))), 0.0), 38.0);
                vec3 organismColor = glassBody + vec3(1.0) * spec * 1.3;

                float alpha = smoothstep(0.02, -0.01, dist);
                finalColor = mix(bgColor, organismColor, alpha);
            }

            float halo = exp(-max(dist, 0.0) * 12.0);
            finalColor += vec3(0.05, 0.4, 0.6) * halo * 0.4;
            gl_FragColor = vec4(finalColor, 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var resolutionHandle = 0
    private var timeHandle = 0
    private var clockHandle = 0
    private var touchHandle = 0
    private val vertexBuffer: FloatBuffer
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var startTime = 0L

    @Volatile var touchX = 0f
    @Volatile var touchY = 0f
    @Volatile var isTouching = 0f

    init {
        val quad = floatArrayOf(-1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, 0f, 1f, -1f, 0f)
        vertexBuffer = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quad)
        vertexBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startTime = SystemClock.uptimeMillis()
        program = ShaderUtils.createProgram(vertexShaderSource, fragmentShaderSource)
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        resolutionHandle = GLES20.glGetUniformLocation(program, "u_Resolution")
        timeHandle = GLES20.glGetUniformLocation(program, "u_Time")
        clockHandle = GLES20.glGetUniformLocation(program, "u_Clock")
        touchHandle = GLES20.glGetUniformLocation(program, "u_Touch")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)

        val time = (SystemClock.uptimeMillis() - startTime) / 1000.0f
        GLES20.glUniform1f(timeHandle, time)
        GLES20.glUniform2f(resolutionHandle, surfaceWidth.toFloat(), surfaceHeight.toFloat())

        val cal = Calendar.getInstance()
        GLES20.glUniform4f(
            clockHandle,
            (cal.get(Calendar.HOUR) % 12).toFloat(),
            cal.get(Calendar.MINUTE).toFloat(),
            cal.get(Calendar.SECOND).toFloat(),
            cal.get(Calendar.MILLISECOND).toFloat()
        )

        val minDim = minOf(surfaceWidth, surfaceHeight).toFloat()
        GLES20.glUniform3f(
            touchHandle,
            (touchX - 0.5f * surfaceWidth) / minDim,
            -(touchY - 0.5f * surfaceHeight) / minDim,
            isTouching
        )

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
}
