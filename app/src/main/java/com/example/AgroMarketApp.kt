package com.example

import android.app.Application
import com.example.data.api.ApiClient
import com.example.data.local.SessionManager
import com.example.data.repository.AgroMarketRepository

class AgroMarketApp : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var repository: AgroMarketRepository
        private set

    val chatRepository get() = repository.chatRepository

    override fun onCreate() {
        super.onCreate()
        instance = this

        sessionManager = SessionManager(applicationContext)
        ApiClient.init(sessionManager)
        repository = AgroMarketRepository(sessionManager)
    }

    companion object {
        lateinit var instance: AgroMarketApp
            private set
    }
}
