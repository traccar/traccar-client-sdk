package org.traccar.client

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class AndroidBatteryProcessor(private val context: Context) : PositionProcessor {

    private val batteryManager: BatteryManager? by lazy {
        context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    override suspend fun process(position: Position): Position {
        var level = position.battery
        var charging = position.charging
        var health = position.batteryHealth
        var voltage = position.batteryVoltage
        var temp = position.batteryTemperature

        // 1. Try checking BatteryManager properties first (if not already set)
        if (level == null) {
            val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (capacity != null && capacity in 0..100) {
                level = capacity
            }
        }
        if (charging == null && batteryManager?.isCharging == true) {
            charging = true
        }

        // 2. Query dynamic sticky intent for health, voltage, temp and level/charging fallbacks
        try {
            val batteryIntent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            batteryIntent?.let { intent ->
                if (level == null) {
                    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (rawLevel >= 0 && scale > 0) {
                        level = (rawLevel * 100) / scale
                    }
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val isPluggedOrCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                        plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

                if (charging == null) {
                    charging = isPluggedOrCharging
                } else if (charging == false) {
                    charging = isPluggedOrCharging
                }

                if (health == null) {
                    val rawHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                    health = when (rawHealth) {
                        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                        else -> "unknown"
                    }
                }

                if (voltage == null) {
                    val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    if (rawVoltage > 0) {
                        voltage = rawVoltage
                    }
                }

                if (temp == null) {
                    val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -999)
                    if (rawTemp > -999) {
                        temp = rawTemp / 10.0
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return position.copy(
            battery = level,
            charging = charging,
            batteryHealth = health,
            batteryVoltage = voltage,
            batteryTemperature = temp,
        )
    }
}
