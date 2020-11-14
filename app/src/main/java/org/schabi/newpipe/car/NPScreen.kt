package org.schabi.newpipe.car

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.google.android.libraries.car.app.CarContext
import com.google.android.libraries.car.app.Screen
import com.google.android.libraries.car.app.model.Action
import com.google.android.libraries.car.app.model.ActionStrip
import com.google.android.libraries.car.app.model.CarColor
import com.google.android.libraries.car.app.model.Template
import com.google.android.libraries.car.app.navigation.model.NavigationTemplate


class NPScreen(carContext: CarContext) : Screen(carContext) {
    override fun getTemplate(): Template {
        val builder = NavigationTemplate.builder()
        builder.setBackgroundColor(CarColor.RED)
        builder.setActionStrip(ActionStrip.builder()
                .addAction(
                        Action.builder()
                                .setTitle("Play/pause")
                                .setOnClickListener {
                                    clickedPlayPause()
                                }.build()
                )
                .build()
        )
        builder.build()
        return builder.build()
    }

    private fun clickedPlayPause() {
        val audio = carContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}