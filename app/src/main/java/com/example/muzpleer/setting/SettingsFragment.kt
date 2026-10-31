package com.example.muzpleer.setting

import androidx.preference.PreferenceFragmentCompat
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.example.muzpleer.R


class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        //пункты, перенесённые из бокового меню: открывают свои экраны (заголовок тулбара ставит MainActivity)
        findPreference<androidx.preference.Preference>("pref_theme")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.themeFragment); true
        }
        findPreference<androidx.preference.Preference>("pref_duplicates")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.duplicatesFragment); true
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
    //так  как нет view, viewLifecycleOwner и Lifecycle.State.RESUMED не работают
}