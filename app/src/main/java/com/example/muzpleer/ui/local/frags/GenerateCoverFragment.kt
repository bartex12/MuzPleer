package com.example.muzpleer.ui.local.frags

import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentGenerateCoverBinding
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
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Экран «Сгенерировать картинку» для обложки: вверху чипсы типа пейзажа (облака, облака и вода,
 * лес, лес и вода), по центру квадратное окно просмотра, внизу панель управления, которая
 * зависит от выбранного типа. «Использовать» открывает экран «Редактировать фото».
 * Генератор тот же, что для фона, но картинка квадратная и целиком видимая (viewTop = 0, viewBottom = сторона).
 */
class GenerateCoverFragment : Fragment() {

    private var _binding: FragmentGenerateCoverBinding? = null
    private val binding get() = _binding!!

    private var kind = LandscapeKind.CLOUD_LAKE
    private var seed = 0
    private var palette = -1          // -1 - как выпадет по зерну
    private var water = 0.40f
    private var clouds = 0.5f
    private var density = 1f
    private var treeH = 1f
    private var moonSize = 1f
    private var moonPos = 0.65f

    private var job: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val regenerate = Runnable { renderPreview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        val s = savedInstanceState
        kind = LandscapeKind.values()[(s?.getInt(KEY_KIND) ?: LandscapeKind.CLOUD_LAKE.ordinal)
            .coerceIn(0, LandscapeKind.values().size - 1)]
        seed = s?.getInt(KEY_SEED) ?: newSeed()
        palette = s?.getInt(KEY_PALETTE) ?: -1
        water = s?.getFloat(KEY_WATER) ?: 0.40f
        clouds = s?.getFloat(KEY_CLOUDS) ?: 0.5f
        density = s?.getFloat(KEY_DENSITY) ?: 1f
        treeH = s?.getFloat(KEY_TREEH) ?: 1f
        moonSize = s?.getFloat(KEY_MOONSIZE) ?: 1f
        moonPos = s?.getFloat(KEY_MOONPOS) ?: 0.65f
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGenerateCoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keepPreviewSquare()
        bindSlider(binding.sbWater, binding.tvWater, { (water * 100).toInt() - 15 }, { water = (it + 15) / 100f }) {
            getString(R.string.gen_bg_water, (water * 100).toInt())
        }
        bindSlider(binding.sbClouds, binding.tvClouds, { (clouds * 100).toInt() }, { clouds = it / 100f }) {
            getString(R.string.gen_bg_clouds, (clouds * 100).toInt())
        }
        bindSlider(binding.sbDensity, binding.tvDensity, { Math.round((density - 0.5f) * 20) }, { density = 0.5f + it * 0.05f }) {
            getString(R.string.gen_bg_density, (density * 100).toInt())
        }
        bindSlider(binding.sbTreeH, binding.tvTreeH, { Math.round((treeH - 0.6f) * 20) }, { treeH = 0.6f + it * 0.05f }) {
            getString(R.string.gen_bg_tree_height, (treeH * 100).toInt())
        }
        bindSlider(binding.sbMoonSize, binding.tvMoonSize, { Math.round((moonSize - 0.5f) * 20) }, { moonSize = 0.5f + it * 0.05f }) {
            getString(R.string.gen_bg_moon_size, (moonSize * 100).toInt())
        }
        bindSlider(binding.sbMoonPos, binding.tvMoonPos, { (moonPos * 100).toInt() }, { moonPos = it / 100f }) {
            getString(R.string.gen_bg_moon_pos, (moonPos * 100).toInt())
        }
        val btnTint = ColorStateList.valueOf(requireContext().panelLightColor())
        binding.btnGenerate.backgroundTintList = btnTint
        binding.btnUse.backgroundTintList = btnTint
        binding.btnGenerate.setOnClickListener {
            seed = newSeed()
            renderPreview()
        }
        binding.btnUse.setOnClickListener { useAsCover() }
        buildKindChips()
        applyKindToPanel()
        renderPreview()
    }

    private fun newSeed() = (System.currentTimeMillis() % 1000000).toInt()

    private fun palNames() = if (kind.hasClouds) CLOUD_PAL_NAMES else FOREST_PAL_NAMES

    //окно просмотра - квадрат максимального размера, который помещается в свободную область
    private fun keepPreviewSquare() {
        binding.previewArea.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val box = _binding?.previewBox ?: return@addOnLayoutChangeListener
            val side = (min(r - l - v.paddingLeft - v.paddingRight, b - t - v.paddingTop - v.paddingBottom))
                .coerceAtLeast(1)
            if (box.layoutParams.width != side || box.layoutParams.height != side) {
                box.layoutParams = (box.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                    width = side; height = side
                }
            }
        }
    }

    private fun chipColors(): ColorStateList {
        val ctx = requireContext()
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(ctx.accentColor(), ctx.panelLightColor())
        )
    }

    private fun styledChip(textRes: Int, checked: Boolean): Chip = Chip(requireContext()).apply {
        id = View.generateViewId()
        setText(textRes)
        isCheckable = true
        isChecked = checked
        chipBackgroundColor = chipColors()
        setTextColor(Color.WHITE)
        chipStrokeColor = ColorStateList.valueOf(Color.WHITE)
        chipStrokeWidth = resources.displayMetrics.density
        isCheckedIconVisible = false
    }

    //чипсы типа пейзажа: при смене палитра сбрасывается, панель перестраивается
    private fun buildKindChips() {
        val kinds = listOf(
            LandscapeKind.CLOUDS to R.string.gen_bg_kind_clouds,
            LandscapeKind.CLOUD_LAKE to R.string.gen_bg_kind_cloud_lake,
            LandscapeKind.FOREST to R.string.gen_bg_kind_forest,
            LandscapeKind.FOREST_LAKE to R.string.gen_bg_kind_forest_lake
        )
        binding.chipsKind.removeAllViews()
        for ((k, res) in kinds) {
            val chip = styledChip(res, k == kind)
            chip.setOnClickListener {
                if (kind == k) return@setOnClickListener
                kind = k
                palette = -1
                applyKindToPanel()
                renderPreview()
            }
            binding.chipsKind.addView(chip)
        }
    }

    //показываем в панели только то, что относится к выбранному типу
    private fun applyKindToPanel() {
        val forest = !kind.hasClouds
        for (row in listOf(binding.rowDensity, binding.rowTreeH, binding.rowMoonSize, binding.rowMoonPos))
            row.visibility = if (forest) View.VISIBLE else View.GONE
        binding.rowWater.visibility = if (kind.hasWater) View.VISIBLE else View.GONE
        binding.rowClouds.visibility = if (kind.hasClouds) View.VISIBLE else View.GONE
        buildPalette()
    }

    private fun buildPalette() {
        val names = if (kind.hasClouds)
            listOf(R.string.gen_bg_pal_any, R.string.gen_bg_pal_day, R.string.gen_bg_pal_sunset,
                R.string.gen_bg_pal_night, R.string.gen_bg_pal_overcast)
        else listOf(R.string.gen_bg_pal_any, R.string.gen_bg_pal_blue, R.string.gen_bg_pal_pink, R.string.gen_bg_pal_gray)
        binding.chipsPalette.removeAllViews()
        for ((i, res) in names.withIndex()) {
            val chip = styledChip(res, (i - 1) == palette)
            chip.setOnClickListener {
                palette = i - 1
                if (kind.hasClouds) {
                    //готовая облачность под палитру: пасмурно - почти сплошные облака
                    clouds = if (palette == 3) 0.9f else 0.5f
                    binding.sbClouds.progress = (clouds * 100).toInt()
                }
                renderPreview()
            }
            binding.chipsPalette.addView(chip)
        }
    }

    private fun bindSlider(bar: SeekBar, label: TextView, get: () -> Int, set: (Int) -> Unit, text: () -> String) {
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

    //картинка квадратная и видна целиком: viewTop = 0, viewBottom = сторона
    private fun params(side: Int) = LandscapeParams(
        kind, seed, side, side, 0f, side.toFloat(), water, clouds, palette, density, treeH, moonSize, moonPos
    )

    private fun renderPreview() {
        handler.removeCallbacks(regenerate)
        job?.cancel()
        val p = params(PREVIEW_SIDE)
        binding.progress.visibility = View.VISIBLE
        job = viewLifecycleOwner.lifecycleScope.launch {
            val ctxJob = coroutineContext[Job]
            val result = withContext(Dispatchers.Default) {
                val gen = createGenerator(p) { ctxJob?.isActive == false }
                gen.generate()?.let { it to gen.paletteUsed() }
            } ?: return@launch
            if (_binding == null) return@launch
            val (bmp, pal) = result
            binding.ivPreview.setImageBitmap(bmp)
            val names = palNames()
            binding.tvInfo.text = getString(R.string.gen_bg_info, seed, getString(names[pal.coerceIn(0, names.size - 1)]))
            binding.progress.visibility = View.GONE
        }
    }

    /** Считает картинку в полном размере и сохраняет JPEG в кэше; null - не вышло или отменили. */
    private suspend fun renderToFile(dirName: String, prefix: String): File? {
        val appContext = requireContext().applicationContext
        val full = params(FULL_SIDE)
        val ctxJob = coroutineContext[Job]
        return withContext(Dispatchers.Default) {
            try {
                val bmp = createGenerator(full) { ctxJob?.isActive == false }.generate() ?: return@withContext null
                val dir = File(appContext.cacheDir, dirName).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "${prefix}_$seed.jpg")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
                file
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnUse.isEnabled = !busy
        binding.btnGenerate.isEnabled = !busy
    }

    //«Использовать»: готовая картинка уходит на экран «Редактировать фото», там выбирается область обложки
    private fun useAsCover() {
        handler.removeCallbacks(regenerate)
        job?.cancel()
        setBusy(true)
        val args = requireArguments()
        job = viewLifecycleOwner.lifecycleScope.launch {
            val file = renderToFile("gen_cover", "cover")
            if (_binding == null) return@launch
            setBusy(false)
            if (file == null) {
                toast(getString(R.string.gen_cover_failed))
                return@launch
            }
            findNavController().navigate(
                R.id.cropCoverFragment,
                Bundle().apply {
                    putString(CropCoverFragment.ARG_URI, Uri.fromFile(file).toString())
                    putBoolean(CropCoverFragment.ARG_LEVEL, args.getBoolean(ARG_LEVEL))
                    putString(CropCoverFragment.ARG_TITLE, args.getString(ARG_TITLE))
                    putInt(CropCoverFragment.ARG_POP_TO, args.getInt(ARG_POP_TO, 0))
                }
            )
        }
    }

    //«Поделиться»: текущая картинка (то же зерно и настройки) в полном размере уходит в другое приложение
    private fun shareCurrent() {
        if (binding.progress.visibility == View.VISIBLE) return
        handler.removeCallbacks(regenerate)
        job?.cancel()
        setBusy(true)
        val appContext = requireContext().applicationContext
        job = viewLifecycleOwner.lifecycleScope.launch {
            val file = renderToFile("shared", "cover")
            val uri = file?.let {
                try {
                    FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", it)
                } catch (e: Exception) { null }
            }
            if (_binding == null) return@launch
            setBusy(false)
            if (uri == null) {
                toast(getString(R.string.gen_cover_failed))
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.gen_cover_share_title)))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_share_generated, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_share_generated) {
            shareCurrent()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? MainActivity)?.updateToolbarTitle(getString(R.string.gen_cover_title))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_KIND, kind.ordinal)
        outState.putInt(KEY_SEED, seed)
        outState.putInt(KEY_PALETTE, palette)
        outState.putFloat(KEY_WATER, water)
        outState.putFloat(KEY_CLOUDS, clouds)
        outState.putFloat(KEY_DENSITY, density)
        outState.putFloat(KEY_TREEH, treeH)
        outState.putFloat(KEY_MOONSIZE, moonSize)
        outState.putFloat(KEY_MOONPOS, moonPos)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(regenerate)
        job?.cancel()
        //возвращаем заголовок экрана, с которого пришли
        arguments?.getString(ARG_TITLE)?.takeIf { it.isNotEmpty() }?.let {
            (activity as? MainActivity)?.updateToolbarTitle(it)
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_LEVEL = "level"
        const val ARG_TITLE = "title"
        /** id экрана обложки, на который вернёмся после сохранения в «Редактировать фото» */
        const val ARG_POP_TO = "popTo"
        private const val KEY_KIND = "gc_kind"
        private const val KEY_SEED = "gc_seed"
        private const val KEY_PALETTE = "gc_palette"
        private const val KEY_WATER = "gc_water"
        private const val KEY_CLOUDS = "gc_clouds"
        private const val KEY_DENSITY = "gc_density"
        private const val KEY_TREEH = "gc_treeh"
        private const val KEY_MOONSIZE = "gc_moonsize"
        private const val KEY_MOONPOS = "gc_moonpos"
        private const val PREVIEW_SIDE = 600
        private const val FULL_SIDE = 1600
        private val CLOUD_PAL_NAMES = intArrayOf(
            R.string.gen_bg_pal_day, R.string.gen_bg_pal_sunset, R.string.gen_bg_pal_night, R.string.gen_bg_pal_overcast
        )
        private val FOREST_PAL_NAMES = intArrayOf(R.string.gen_bg_pal_blue, R.string.gen_bg_pal_pink, R.string.gen_bg_pal_gray)
    }
}
