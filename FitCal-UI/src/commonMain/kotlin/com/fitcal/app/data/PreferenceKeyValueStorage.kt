package com.fitcal.app.data

import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference
import com.fitcal.shared.data.KeyValueStorage

class PreferenceKeyValueStorage : KeyValueStorage {
    override fun getString(key: String, defaultValue: String): String {
        return loadPreference(key, defaultValue)
    }

    override fun putString(key: String, value: String) {
        savePreference(key, value)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        val str = loadPreference(key, defaultValue.toString())
        return str.toIntOrNull() ?: defaultValue
    }

    override fun putInt(key: String, value: Int) {
        savePreference(key, value.toString())
    }

    override fun remove(key: String) {
        savePreference(key, "")
    }
}
