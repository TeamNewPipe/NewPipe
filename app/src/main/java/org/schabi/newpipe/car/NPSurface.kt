package org.schabi.newpipe.car

import android.graphics.Rect
import android.view.Surface
import android.view.SurfaceView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.car.app.AppManager
import com.google.android.libraries.car.app.CarContext
import com.google.android.libraries.car.app.SurfaceContainer
import com.google.android.libraries.car.app.SurfaceListener
import java.lang.ref.WeakReference
import java.util.*

class NPSurface(val carContext: CarContext, lifecycle: Lifecycle) {

    private val myLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            carContext.getCarService(AppManager::class.java).setSurfaceListener(mySurfaceListener)
        }
    }

    val mySurfaceListener = object : SurfaceListener {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer.surface
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
        }

        override fun onStableAreaChanged(stableArea: Rect) {
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surface = null
        }
    }

    init {
        lifecycle.addObserver(myLifecycleObserver)
    }

    companion object {
        var surface: Surface? = null
    }
}