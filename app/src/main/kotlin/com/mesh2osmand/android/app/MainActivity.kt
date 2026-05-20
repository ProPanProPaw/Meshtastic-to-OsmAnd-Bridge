/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mesh2osmand.android.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mesh2osmand.android.app.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        startForegroundService()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.sendBtn).setOnClickListener { _ ->
            /*meshService?.let {
                try {
                    it.send(
                        DataPacket(
                            to = DataPacket.ID_BROADCAST,
                            bytes = \"Hello from MeshServiceExample\".toByteArray(),
                            dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                            from = DataPacket.ID_LOCAL,
                            time = System.currentTimeMillis(),
                            id = 0,
                            status = MessageStatus.UNKNOWN,
                            hopLimit = 3,
                            channel = 0,
                            wantAck = true,
                        ),
                    )
                    Log.d(TAG, \"Message sent successfully\")
                } catch (e: Exception) {
                    Log.e(TAG, \"Failed to send message\", e)
                }
            } ?: Log.w(TAG, \"MeshService is not bound, cannot send message\")*/
        }
    }

    private fun startForegroundService(){
        val intent = Intent(this, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
    }


    override fun onDestroy() {
        super.onDestroy()
    }
}
