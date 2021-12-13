package com.byagowi.persiancalendar.ui.shared

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.isVisible
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.global.coordinates
import com.cepmuvakkit.times.posAlgo.SunMoonPosition

class MoonView(context: Context, attrs: AttributeSet? = null) : View(context, attrs),
    ValueAnimator.AnimatorUpdateListener {

    private val solarDraw = SolarDraw(context)
    private var animator: ValueAnimator? = null
    private var moonPhase = .0f
    var jdn = Jdn.today()
        set(value) {
            field = value
            val coordinates = coordinates ?: return
            animator?.removeAllUpdateListeners()
            val dest = SunMoonPosition(
                jdn.value.toDouble(), coordinates.latitude, coordinates.longitude,
                coordinates.elevation, 0.0
            ).moonPhase.toFloat()
            if (!isVisible) {
                moonPhase = dest
                return
            }
            ValueAnimator.ofFloat(if (dest == moonPhase) 0f else moonPhase, dest).also {
                animator = it
                it.duration = resources.getInteger(android.R.integer.config_longAnimTime).toLong()
                it.interpolator = AccelerateDecelerateInterpolator()
                it.addUpdateListener(this)
            }.start()
            postInvalidate()
        }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas ?: return)
        val cx = width / 2f
        solarDraw.moon(canvas, moonPhase.toDouble(), cx, cx, cx)
    }

    override fun onAnimationUpdate(valueAnimator: ValueAnimator?) {
        moonPhase = (valueAnimator?.animatedValue as? Float) ?: .0f
        postInvalidate()
    }
}