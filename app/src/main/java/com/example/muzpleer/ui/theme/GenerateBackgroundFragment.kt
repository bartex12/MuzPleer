package com.example.muzpleer.ui.theme

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentGenerateBackgroundBinding
import com.example.muzpleer.util.AppBackground
import com.example.muzpleer.util.accentColor
import com.example.muzpleer.util.landscape.LandscapeKind
import com.example.muzpleer.util.landscape.LandscapeParams
import com.example.muzpleer.util.landscape.createGenerator
import com.example.muzpleer.util.panelLightColor
import com.example.muzpleer.util.toast
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

//экран «Сгенерировать фон»: облака / лес, с водой или без; палитра, доля воды, облачность, просмотр здесь же
class GenerateBackgroundFragment : Fragment() {

    private var _binding: FragmentGenerateBackgroundBinding? = null
    private val binding get() = _binding!!

    private var seed = 0
    private var palette = -1          // -1 - как выпадет по зерну
    private var water = 0.40f
    private var clouds = 0.5f
    private var kind = LandscapeKind.CLOUD_LAKE

    //размер окна приложения и видимая между тулбаром и плеером часть
    private var winW = 0
    private var winH = 0
    private var viewTop = 0f
    private var viewBottom = 0f

    private var job: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val regenerate = Runnable { renderPreview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = LandscapeKind.values()[(arguments?.getInt(ARG_KIND) ?: LandscapeKind.CLOUD_LAKE.ordinal)
            .coerceIn(0, LandscapeKind.values().size - 1)]
        seed = savedInstanceState?.getInt(KEY_SEED) ?: newSeed()
        palette = savedInstanceState?.getInt(KEY_PALETTE) ?: -1
        water = savedInstanceState?.getFloat(KEY_WATER) ?: 0.40f
        clouds = savedInstanceState?.getFloat(KEY_CLOUDS) ?: 0.5f
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGenerateBackgroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        measureWindow()
        buildPalette()
        bindSlider(binding.sbWater, binding.tvWater, { (water * 100).toInt() - 15 }, { water = (it + 15) / 100f }) {
            getString(R.string.gen_bg_water, (water * 100).toInt())
        }
        bindSlider(binding.sbClouds, binding.tvClouds, { (clouds * 100).toInt() }, { clouds = it / 100f }) {
            getString(R.string.gen_bg_clouds, (clouds * 100).toInt())
        }
        binding.rowWater.visibility = if (kind.hasWater) View.VISIBLE else View.GONE
        binding.rowClouds.visibility = if (kind.hasClouds) View.VISIBLE else View.GONE
        val btnTint = ColorStateList.valueOf(requireContext().panelLightColor())
        binding.btnGenerate.backgroundTintList = btnTint
        binding.btnUse.backgroundTintList = btnTint
        binding.btnGenerate.setOnClickListener {
            seed = newSeed()
            renderPreview()
        }
        binding.btnUse.setOnClickListener { useAsBackground() }
        renderPreview()
    }

    private fun palNames() = if (kind.hasClouds) CLOUD_PAL_NAMES else FOREST_PAL_NAMES

    private fun newSeed() = (System.currentTimeMillis() % 1000000).toInt()

    //размер окна и положение видимой области берём у настоящих экранов приложения
    private fun measureWindow() {
        val act = requireActivity()
        val decor = act.window.decorView
        val host = act.findViewById<View>(R.id.nav_host_fragment_content_main)
        winW = decor.width
        winH = decor.height
        if (winW <= 0 || winH <= 0) {
            val dm = resources.displayMetrics
            winW = dm.widthPixels; winH = dm.heightPixels
        }
        if (host != null && host.height > 0) {
            val d = IntArray(2); val h = IntArray(2)
            decor.getLocationOnScreen(d); host.getLocationOnScreen(h)
            viewTop = (h[1] - d[1]).toFloat()
            viewBottom = viewTop + host.height
        } else {
            viewTop = 0f; viewBottom = winH.toFloat()
        }
    }

    private fun buildPalette() {
        val ctx = requireContext()
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val bg = ColorStateList(states, intArrayOf(ctx.accentColor(), ctx.panelLightColor()))
        val names = if (kind.hasClouds)
            listOf(R.string.gen_bg_pal_any, R.string.gen_bg_pal_day, R.string.gen_bg_pal_sunset,
                R.string.gen_bg_pal_night, R.string.gen_bg_pal_overcast)
        else listOf(R.string.gen_bg_pal_any, R.string.gen_bg_pal_blue, R.string.gen_bg_pal_pink, R.string.gen_bg_pal_gray)
        binding.chipsPalette.removeAllViews()
        for ((i, res) in names.withIndex()) {
            val chip = Chip(ctx).apply {
                id = View.generateViewId()
                setText(res)
                isCheckable = true
                isChecked = (i - 1) == palette
                chipBackgroundColor = bg
                setTextColor(Color.WHITE)
                chipStrokeColor = ColorStateList.valueOf(Color.WHITE)
                chipStrokeWidth = resources.displayMetrics.density
                isCheckedIconVisible = false
                setOnClickListener {
                    palette = i - 1
                    if (kind.hasClouds) {
                        //готовая облачность под палитру: пасмурно - почти сплошные облака
                        clouds = if (palette == 3) 0.9f else 0.5f
                        binding.sbClouds.progress = (clouds * 100).toInt()
                    }
                    renderPreview()
                }
            }
            binding.chipsPalette.addView(chip)
        }
    }

    private fun bindSlider(
        bar: SeekBar, label: android.widget.TextView, get: () -> Int, set: (Int) -> Unit, text: () -> String
    ) {
        bar.progress = get()
        label.text = text()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                set(progress)
                label.text = text()
                if (fromUser) {
                    //картинка пересчитывается, когда движение ползунка на миг остановилось
                    handler.removeCallbacks(regenerate)
                    handler.postDelayed(regenerate, 200)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun params() = LandscapeParams(kind, seed, winW, winH, viewTop, viewBottom, water, clouds, palette)

    //предпросмотр считается в уменьшенном размере, чтобы ползунки отзывались быстро
    private fun renderPreview() {
        handler.removeCallbacks(regenerate)
        job?.cancel()
        val scale = min(1f, PREVIEW_LONG_SIDE / max(winW, winH))
        val small = params().scaled(scale)
        binding.progress.visibility = View.VISIBLE
        job = viewLifecycleOwner.lifecycleScope.launch {
            val ctxJob = coroutineContext[Job]
            val result = withContext(Dispatchers.Default) {
                val gen = createGenerator(small) { ctxJob?.isActive == false }
                gen.generate()?.let { it to gen.paletteUsed() }
            } ?: return@launch
            if (_binding == null) return@launch
            val (bmp, pal) = result
            //показываем только видимую часть между тулбаром и плеером
            val top = small.viewTop.toInt().coerceIn(0, bmp.height - 1)
            val h = (small.viewBottom.toInt() - top).coerceIn(1, bmp.height - top)
            binding.ivPreview.setImageBitmap(Bitmap.createBitmap(bmp, 0, top, bmp.width, h))
            binding.tvInfo.text = getString(R.string.gen_bg_info, seed, getString(palNames()[pal.coerceIn(0, palNames().size - 1)]))
            binding.progress.visibility = View.GONE
        }
    }

    //готовая картинка в полном размере становится одним из «Ваших фото»; фон окна ставится, «Применить» - на экране темы
    private fun useAsBackground() {
        handler.removeCallbacks(regenerate)
        job?.cancel()
        binding.btnUse.isEnabled = false
        binding.btnGenerate.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        val appContext = requireContext().applicationContext
        val full = params().scaled(min(1f, FULL_LONG_SIDE / max(winW, winH)))
        job = viewLifecycleOwner.lifecycleScope.launch {
            val ctxJob = coroutineContext[Job]
            val path = withContext(Dispatchers.Default) {
                val bmp = createGenerator(full) { ctxJob?.isActive == false }.generate() ?: return@withContext null
                AppBackground.saveGenerated(appContext, bmp).also { bmp.recycle() }
            }
            if (_binding == null) return@launch
            if (path == null) {
                toast(getString(R.string.gen_bg_failed))
                binding.btnUse.isEnabled = true
                binding.btnGenerate.isEnabled = true
                binding.progress.visibility = View.GONE
                return@launch
            }
            val current = AppBackground.pending ?: AppBackground.load(appContext)
            val spec = if (AppBackground.isReplaceSelected(appContext))
                AppBackground.withPhotoReplacingSelected(current, path)
            else AppBackground.withNewPhoto(current, path)
            AppBackground.pending = spec
            activity?.let { AppBackground.applyToWindow(it, spec) }
            //сразу на «Сменить тему», где доступна кнопка «Применить»
            val nav = findNavController()
            if (!nav.popBackStack(R.id.themeFragment, false)) nav.navigateUp()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SEED, seed)
        outState.putInt(KEY_PALETTE, palette)
        outState.putFloat(KEY_WATER, water)
        outState.putFloat(KEY_CLOUDS, clouds)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(regenerate)
        job?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SEED = "gen_seed"
        private const val KEY_PALETTE = "gen_palette"
        private const val KEY_WATER = "gen_water"
        private const val KEY_CLOUDS = "gen_clouds"
        const val ARG_KIND = "kind"
        private const val PREVIEW_LONG_SIDE = 800f
        private const val FULL_LONG_SIDE = 2400f
        private val CLOUD_PAL_NAMES = intArrayOf(
            R.string.gen_bg_pal_day, R.string.gen_bg_pal_sunset, R.string.gen_bg_pal_night, R.string.gen_bg_pal_overcast
        )
        private val FOREST_PAL_NAMES = intArrayOf(R.string.gen_bg_pal_blue, R.string.gen_bg_pal_pink, R.string.gen_bg_pal_gray)
    }
}
