package sg.edumate.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import sg.edumate.app.security.SecretStore
import javax.inject.Inject

/** Adds the backend-issued access token (from OIDC sign-in, e.g. Google Workspace for Education). */
class AuthInterceptor @Inject constructor(private val secrets: SecretStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runCatching { secrets.get(SecretStore.ACCESS_TOKEN)?.decodeToString() }.getOrNull()
        val req = chain.request().newBuilder()
            .header("X-Client", "edumate-android")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return chain.proceed(req)
    }
}
