package com.mobplayer.remote.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthTokenRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(tvIdentifier: String, token: String) {
        sharedPreferences.edit()
            .putString(KEY_TOKEN_PREFIX + tvIdentifier, token)
            .apply()
    }

    fun getToken(tvIdentifier: String): String? {
        return sharedPreferences.getString(KEY_TOKEN_PREFIX + tvIdentifier, null)
    }

    fun clearToken(tvIdentifier: String) {
        sharedPreferences.edit()
            .remove(KEY_TOKEN_PREFIX + tvIdentifier)
            .apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "mobplayer_remote_secure_prefs"
        private const val KEY_TOKEN_PREFIX = "token_"
    }
}
