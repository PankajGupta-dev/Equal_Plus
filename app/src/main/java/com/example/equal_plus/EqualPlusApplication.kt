package com.example.equal_plus

import android.app.Application
import com.example.equal_plus.callscreening.AppContextProvider

class EqualPlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextProvider.applicationContext = this
        com.example.equal_plus.telephony.LiveCallSessionManager.init(this)
    }
}
