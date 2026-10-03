package xyz.plcliangpicup.phigrosscore.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Reuse TLS connections without letting image prefetch occupy the API request queue. */
internal object ResourceHttp {
    private val connections = ConnectionPool(8, 5, TimeUnit.MINUTES)
    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectionPool(connections)
        .dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 8 })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
}
