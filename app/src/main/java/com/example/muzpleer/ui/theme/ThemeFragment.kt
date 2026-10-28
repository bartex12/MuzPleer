package com.example.muzpleer.ui.theme

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentThemeBinding
import com.example.muzpleer.util.ThemeManager
import com.example.muzpleer.util.accentColor
import com.google.android.material.tabs.TabLayout

//экран «Сменить тему»: выбор цвета акцента с предпросмотром
class ThemeFragment : Fragment() {

    private var _binding: FragmentThemeBinding? = null
    private val binding get() = _binding!!

    private var selected = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = savedInstanceState?.getInt(KEY_SELECTED) ?: ThemeManager.getAccentIndex(requireContext())

        buildColorRow()
        renderPreview()

        binding.btnApplyTheme.setOnClickListener {
            ThemeManager.setAccentIndex(requireContext(), selected)
            //пересоздаём экран, чтобы тема применилась везде
            requireActivity().recreate()
        }
    }

    private fun buildColorRow() {
        val row = binding.accentColorsRow
        row.removeAllViews()
        val size = (40 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        val stroke = (3 * resources.displayMetrics.density).toInt()
        for (i in ThemeManager.accentColorRes.indices) {
            val dot = View(requireContext())
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ThemeManager.colorOf(requireContext(), i))
                if (i == selected) setStroke(stroke, Color.WHITE)
            }
            dot.setOnClickListener {
                selected = i
                buildColorRow()
                renderPreview()
            }
            row.addView(dot)
        }
    }

    //миниатюра строится из настоящих макетов в контексте с выбранным акцентом
    private fun renderPreview() {
        val themed = ContextThemeWrapper(requireContext(), ThemeManager.accentOverlays[selected])
        val inflater = LayoutInflater.from(themed)
        val preview = inflater.inflate(R.layout.layout_theme_preview, binding.themePreviewContainer, false)

        val list = preview.findViewById<LinearLayout>(R.id.previewList)
        val demo = listOf(
            Triple("Песня играет", "Исполнитель", "03:21"),
            Triple("Песня о настоящем индейце", "Ноль", "04:34"),
            Triple("Звёздочка моя ясная", "Цветы", "04:45"),
            Triple("Hotel California", "Eagles", "06:31"),
            Triple("Wind Of Change", "Scorpions", "05:12"),
            Triple("Smoke on the Water", "Deep Purple", "05:40")
        )
        for ((index, item) in demo.withIndex()) {
            val row = inflater.inflate(R.layout.item_music, list, false)
            row.findViewById<TextView>(R.id.trackTitle).text = item.first
            row.findViewById<TextView>(R.id.trackArtist).text = item.second
            row.findViewById<TextView>(R.id.trackDuration).text = item.third
            row.isSelected = index == 0 //как у играющего трека
            list.addView(row)
        }

        val tabs = preview.findViewById<TabLayout>(R.id.previewTabs)
        for (name in listOf("ТРЕКИ", "ПЛЕЙЛИСТЫ", "АЛЬБОМЫ")) {
            tabs.addTab(tabs.newTab().setText(name))
        }
        val accent = themed.accentColor()
        tabs.setTabTextColors(Color.WHITE, accent)
        tabs.setSelectedTabIndicatorColor(accent)

        //предпросмотр только показывает, нажатия ему не нужны
        preview.isEnabled = false
        disableTouch(preview)

        binding.themePreviewContainer.removeAllViews()
        binding.themePreviewContainer.addView(preview)
    }

    private fun disableTouch(view: View) {
        view.isClickable = false
        view.isFocusable = false
        if (view is ViewGroup) {
            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            view.setOnTouchListener { _, _ -> true }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED, selected)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SELECTED = "selected_accent"
    }
}
