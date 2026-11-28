package com.aurora.player.study

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build

/**
 * Starts Android's user-approved screen-pinning flow for a focused study session.
 *
 * On ordinary devices Android displays its own pinning confirmation/instructions.
 * Fully managed Lock Task kiosk mode is only available when an administrator has
 * allowlisted this package as a lock-task package. This helper never attempts to
 * bypass Android's system confirmation or device-owner policy.
 */
object StudyLockController {
    data class LockResult(
        val started: Boolean,
        val managedKioskAvailable: Boolean,
        val message: String
    )

    fun enter(activity: Activity): LockResult {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) {
            return LockResult(false, false, "تعذر بدء وضع التركيز لأن الشاشة أُغلقت.")
        }

        val policy = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val managedKiosk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            policy.isLockTaskPermitted(activity.packageName)
        } else {
            false
        }

        return try {
            // If the device is not managed/allowlisted, Android invokes its
            // standard screen-pinning consent flow. The user retains system escape controls.
            activity.startLockTask()
            LockResult(
                started = true,
                managedKioskAvailable = managedKiosk,
                message = if (managedKiosk) {
                    "تم تفعيل وضع التركيز المُدار."
                } else {
                    "طُلب من Android تثبيت التطبيق. أكّد التثبيت من شاشة النظام."
                }
            )
        } catch (error: SecurityException) {
            LockResult(false, managedKiosk, "لم يسمح Android بتفعيل تثبيت التطبيق: ${error.message.orEmpty()}")
        } catch (error: IllegalStateException) {
            LockResult(false, managedKiosk, "لا يمكن تفعيل تثبيت التطبيق في الحالة الحالية.")
        }
    }

    fun exit(activity: Activity): Boolean {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return false
        return try {
            activity.stopLockTask()
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun isManagedKioskAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return policy.isLockTaskPermitted(context.packageName)
    }
}
