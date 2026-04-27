package com.meshtastic.android.app


import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class TimeHelper {


    companion object {

        fun epochToIso8601(timeInMs: Long, format: String = "dd MMM yyyy HH:mm:ss"): String {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            return sdf.format(Date(timeInMs))
        }

        fun toSecondsAgo(timeMs: Int): Int {
            val currentTime = System.currentTimeMillis();
            val secondsAgo = (currentTime / 1000 - timeMs).toInt();

            return secondsAgo
        }

        fun toMomentAgo(timeMs: Int): MomentAgo {

            val secondsAgo = toSecondsAgo(timeMs);

            return MomentAgo(secondsAgo)
        }
    }
}

class MomentAgo {

    val value: Int
    val prefix: String

    constructor(secondsAgo: Int) {

        if (secondsAgo < 1) {
            value = 0
            prefix = "now"
        }
        else if (secondsAgo < 60) {
            value = secondsAgo
            prefix = "s ago"
        }
        else if (secondsAgo < 3600) {
            value = (secondsAgo / 60)
            prefix = "m ago"
        }
        else if (secondsAgo < 24 * 60 * 60) {
            value = (secondsAgo / 3600)
            prefix = "h ago"
        } else {
            value = (secondsAgo / (24 * 60 * 60))
            prefix = "d ago"

        }
    }

    override fun toString(): String {

        if(value == 0) {
            return prefix
        }

        return "${value}${prefix}"
    }
}