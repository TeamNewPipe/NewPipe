/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.network

import de.jensklingenberg.ktorfit.http.GET
import net.newpipe.app.model.NewPipeVersionResponse

interface NewPipeService {
    @GET("api/data.json")
    suspend fun getVersionInfo(): NewPipeVersionResponse
}
