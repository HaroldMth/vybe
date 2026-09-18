package io.github.zyrouge.symphony.services

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import io.github.zyrouge.symphony.MainActivity
import io.github.zyrouge.symphony.Symphony

class Permissions(private val symphony: Symphony) {
    data class State(
        val required: List<String>,
        val granted: List<String>,
        val denied: List<String>,
    ) {
        fun hasAll() = denied.isEmpty()
    }

    fun handle(activity: MainActivity) {
        val state = getState(activity)
        if (state.hasAll()) {
            return
        }
        val contract = ActivityResultContracts.RequestMultiplePermissions()
        activity.registerForActivityResult(contract) {}.launch(state.denied.toTypedArray())
    }

    /**
     * Storage permissions needed before writing a downloaded song to public storage.
     * - API 33+: READ_MEDIA_AUDIO (so downloads can be listed/read back)
     * - API 29-32: scoped storage covers MediaStore inserts, nothing to request
     * - API <29: legacy full storage access is required for direct file writes
     */
    fun getStoragePermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyList()
    }

    fun hasStoragePermissions(activity: MainActivity): Boolean {
        val required = getStoragePermissions()
        if (required.isEmpty()) return true
        return required.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun getRequiredPermissions(): List<String> {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return required
    }

    private fun getState(activity: MainActivity): State {
        val required = getRequiredPermissions()
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        required.forEach {
            if (activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED) {
                granted.add(it)
            } else {
                denied.add(it)
            }
        }
        return State(required = required, granted = granted, denied = denied)
    }
}
