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
        findPreference<androidx.preference.Preference>("pref_splash_image")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.splashImageFragment); true
        }
        findPreference<androidx.preference.Preference>("pref_duplicates")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.duplicatesFragment); true
        }
    }

    override fun onResume() {
        super.onResume()
        //коротко показываем, какая картинка выбрана
        val spec = com.example.muzpleer.util.SplashImage.load(requireContext())
        findPreference<androidx.preference.Preference>("pref_splash_image")?.setSummary(
            when (spec.mode) {
                com.example.muzpleer.util.SplashImage.MODE_CUSTOM -> R.string.pref_splash_image_summary_custom
                com.example.muzpleer.util.SplashImage.MODE_BACKGROUND -> R.string.pref_splash_image_summary_background
                else -> R.string.pref_splash_image_summary_default
            }
        )
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
    //так  как нет view, viewLifecycleOwner и Lifecycle.State.RESUMED не работают
}