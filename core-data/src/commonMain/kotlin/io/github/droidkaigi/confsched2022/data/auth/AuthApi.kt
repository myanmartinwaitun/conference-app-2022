package io.github.droidkaigi.confsched2022.data.auth

import io.github.droidkaigi.confsched2022.data.SettingsDatastore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first

class AuthApi(
    private val httpClient: HttpClient,
    private val userDataStore: SettingsDatastore,
    private val authenticator: Authenticator
) {
    suspend fun authIfNeeded() {
        var idToken = authenticator.currentUser()?.idToken

        if (idToken == null) {
            // not authenticated
            idToken = authenticator.signInAnonymously()?.idToken.orEmpty()
        }
        userDataStore.setIdToken(idToken)

        if (userDataStore.isAuthenticated().first() == true) {
            return // Already registered on server
        }
        if (idToken.isBlank()) {
            return // Invalid id token
        }
        registerToServer(idToken)
        userDataStore.setAuthenticated(true)
    }

    private suspend fun registerToServer(createdIdToken: String) {
        runCatching {
            // Use httpClient for bypass auth process
            httpClient
                .post("https://ssot-api-staging.an.r.appspot.com/accounts") {
                    header(HttpHeaders.Authorization, "Bearer $createdIdToken")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
        }.getOrElse {
            if (it !is ResponseException || it.response.status != HttpStatusCode.Conflict) {
                throw it
            }
        }
    }
}
