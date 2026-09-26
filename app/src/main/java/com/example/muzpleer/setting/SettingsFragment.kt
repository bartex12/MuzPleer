package com.example.muzpleer.setting

import androidx.preference.PreferenceFragmentCompat
import android.os.Bundle
import com.example.muzpleer.R


class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
    //так  как нет view, viewLifecycleOwner и Lifecycle.State.RESUMED не работают
}