package `in`.innovaticshub.notepad

import android.app.Application
import `in`.innovaticshub.notepad.di.AppContainer

/**
 * Application entry point owning the manual dependency container.
 */
class DrawlyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
