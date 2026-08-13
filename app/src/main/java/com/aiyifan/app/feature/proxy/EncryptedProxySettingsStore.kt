package com.aiyifan.app.feature.proxy

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedProxySettingsStore(context: Context) : ProxySettingsStore {
    private val preferences = createPreferences(context)

    override fun hasConnectedBefore(): Boolean =
        preferences.getBoolean(KEY_HAS_CONNECTED_BEFORE, false)

    override fun saveHasConnectedBefore(value: Boolean) {
        preferences.edit().putBoolean(KEY_HAS_CONNECTED_BEFORE, value).apply()
    }

    override fun readSubscriptionUrl(): String? = preferences.getString(KEY_SUBSCRIPTION_URL, null)

    override fun saveSubscriptionUrl(value: String) {
        preferences.edit().putString(KEY_SUBSCRIPTION_URL, value).apply()
    }

    override fun readSelectedNodeId(): String? = preferences.getString(KEY_SELECTED_NODE_ID, null)

    override fun saveSelectedNodeId(value: String?) {
        preferences.edit().putString(KEY_SELECTED_NODE_ID, value).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "encrypted_proxy_settings"
        const val KEY_HAS_CONNECTED_BEFORE = "has_connected_before"
        const val KEY_SUBSCRIPTION_URL = "subscription_url"
        const val KEY_SELECTED_NODE_ID = "selected_node_id"

        fun createPreferences(context: Context) = try {
            encryptedPreferences(context)
        } catch (error: Throwable) {
            if (!EncryptedSettingsRecoveryPolicy.shouldClear(error)) throw error
            context.deleteSharedPreferences(PREFERENCES_NAME)
            encryptedPreferences(context)
        }

        fun encryptedPreferences(context: Context) = EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
