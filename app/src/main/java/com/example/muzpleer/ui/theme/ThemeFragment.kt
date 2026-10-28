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
        val stroke = (3 * resources.displayMetrics.density).toInt() //толстое кольцо у выбранного
        val thin = (1 * resources.displayMetrics.density).toInt().coerceAtLeast(1) //тонкое у остальных, чтобы цвет не терялся на фоне
        for (i in ThemeManager.accentColorRes.indices) {
            val dot = View(requireContext())
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ThemeManager.colorOf(requireContext(), i))
                setStroke(if (i == selected) stroke else thin, Color.WHITE)
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
            Triple("Нет тебя прекрасней", "Юрий Антонов", "04:31"),
            Triple("Есть глаза у цветов", "Цветы", "04:13"),
            Triple("I Will Survive", "Gloria Gaynor", "04:47"),
            Triple("Sunny", "Boney M.", "04:03"),
            Triple("Wind Of Change", "Scorpions", "05:12"),
            Triple("Smoke on the Water", "Deep Purple", "05:40"),
            Triple("July Morning", "Uriah Heep", "10:29"),
            Triple("Hotel California", "Eagles", "06:31"),
            Triple("Show Must Go On", "Queen", "04:31"),
            Triple("Stairway to Heaven", "Led Zeppelin", "08:02"),
            Triple("Yesterday", "The Beatles", "02:05"),
            Triple("Imagine", "John Lennon", "03:04"),
            Triple("Nothing Else Matters", "Metallica", "06:29"),
            Triple("Believer", "Imagine Dragons", "03:24"),
            Triple("Billie Jean", "Michael Jackson", "04:54"),
            Triple("Another Brick in the Wall", "Pink Floyd", "03:59"),
            Triple("Bohemian Rhapsody", "Queen", "05:55"),
            Triple("Come as You Are", "Nirvana", "03:39"),
            Triple("Страна Лимония", "Сплин", "03:48"),
            Triple("Группа крови", "Кино", "04:47"),
            Triple("Дорога в облака", "Сплин", "04:12")
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

        binding.themePreviewContainer.removeAllViews()
        binding.themePreviewContainer.addView(preview)
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
