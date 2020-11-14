package org.schabi.newpipe.car

import android.content.Intent
import com.google.android.libraries.car.app.CarAppService
import com.google.android.libraries.car.app.Screen

class NPCarAppService : CarAppService() {
    override fun onCreateScreen(intent: Intent): Screen {
        NPSurface(carContext, lifecycle)
        return NPScreen(carContext)
    }

}