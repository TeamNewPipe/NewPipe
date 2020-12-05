package org.schabi.newpipe

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class NewPipeTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, TestApp::class.java.name, context)
    }
}
