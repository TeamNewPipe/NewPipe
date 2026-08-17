/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.newpipe.app.network.NewPipeService
import net.newpipe.app.network.createNewPipeService
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
object NetworkModule {

    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Singleton
    fun provideKtorfit(httpClient: HttpClient): Ktorfit {
        return Ktorfit.Builder()
            .baseUrl("https://newpipe.net/")
            .httpClient(httpClient)
            .build()
    }

    @Singleton
    fun provideNewPipeService(ktorfit: Ktorfit): NewPipeService {
        return ktorfit.createNewPipeService()
    }
}
