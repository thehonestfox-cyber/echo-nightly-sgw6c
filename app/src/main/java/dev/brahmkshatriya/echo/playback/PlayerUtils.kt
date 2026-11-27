package dev.brahmkshatriya.echo.playback

import android.content.Context
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Track

object PlayerUtils {
    fun Track.playableString(context: Context) = when (val play = isPlayable) {
        is Track.Playable.No -> context.getString(R.string.not_playable_x, play.reason)
        Track.Playable.Yes -> null
        Track.Playable.RegionLocked -> context.getString(R.string.unavailable_in_your_region)
        Track.Playable.Unreleased -> if (releaseDate != null) context.getString(
            R.string.releases_on_x, releaseDate.toString()
        ) else context.getString(R.string.not_yet_released)
    }

    fun Long.toTimeString(): String {
        val totalSeconds = this / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}
