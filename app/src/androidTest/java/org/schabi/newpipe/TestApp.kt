package org.schabi.newpipe

import org.schabi.newpipe.di.AppModule

class TestApp : App() {
    override fun getAppComponent(): TestAppComponent = DaggerTestAppComponent.builder()
        .appModule(AppModule(this))
        .testRoomModule(TestRoomModule())
        .build()

    override fun onCreate() {
        super.onCreate()

        app = this
    }

    companion object {
        @JvmStatic
        lateinit var app: TestApp
    }
}
