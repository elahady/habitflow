package com.roziqrizal.habitflow.testing

import android.content.SharedPreferences

/**
 * SharedPreferences di memori untuk tes JVM (android.jar hanya berisi kerangka). Satu objek mewakili satu berkas:
 * buat objek baru dengan [snapshot] yang sama untuk meniru proses app yang dimulai ulang.
 */
class FakeSharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val values = HashMap<String, Any?>(initial)

    /** Isi saat ini, untuk menghidupkan berkas yang sama di objek baru. */
    fun snapshot(): Map<String, Any?> = HashMap(values)

    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = ArrayList<() -> Unit>()

        private fun queue(change: () -> Unit) = apply { pending += change }

        override fun putString(key: String, value: String?) = queue { values[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = queue { this@FakeSharedPreferences.values[key] = values }
        override fun putInt(key: String, value: Int) = queue { values[key] = value }
        override fun putLong(key: String, value: Long) = queue { values[key] = value }
        override fun putFloat(key: String, value: Float) = queue { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = queue { values[key] = value }
        override fun remove(key: String) = queue { values.remove(key) }
        override fun clear() = queue { values.clear() }
        override fun commit(): Boolean {
            pending.forEach { it() }
            pending.clear()
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
