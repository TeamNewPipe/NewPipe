/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2024 <code(at)newpipe-ev.de>
 *
 * NewPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.newpipe.newplayer.testapp

import android.app.Application
import android.util.Log
import androidx.core.graphics.drawable.IconCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.NewPlayerImpl
import net.newpipe.newplayer.repository.CachingRepository
import net.newpipe.newplayer.repository.PlaceHolderRepository
import net.newpipe.newplayer.repository.PrefetchingRepository
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NewPlayerComponent {
    @Provides
    @Singleton
    fun provideNewPlayer(app: Application): NewPlayer {
        val player = NewPlayerImpl(
            app = app,
            repository = PrefetchingRepository(CachingRepository(PlaceHolderRepository())),
            notificationIcon = IconCompat.createWithResource(app, net.newpipe.newplayer.R.drawable.new_player_tiny_icon),
            playerActivityClass = MainActivity::class.java,
            // rescueStreamFault = …
        )
        if (app is App) {
            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    player.errorFlow.collect { e ->
                        Log.e("NewPlayerException", e.stackTraceToString())
                    }
                }
            }
        }
        return player
    }
}
