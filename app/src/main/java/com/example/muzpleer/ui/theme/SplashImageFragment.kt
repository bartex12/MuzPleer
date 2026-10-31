package com.example.muzpleer.ui.theme

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentSplashImageBinding
import com.example.muzpleer.util.AppBackground
import com.example.muzpleer.util.SplashImage
import com.example.muzpleer.util.SplashSpec
import com.example.muzpleer.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Экран «Картинка экрана загрузки»: большое окно просмотра, свои картинки (до трёх) и «взять фон приложения». */
class SplashImageFragment : Fragment() {

    private var _binding: FragmentSplashImageBinding? = null
    private val binding get() = _binding!!
    private lateinit var spec: SplashSpec

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                AppBackground.importPhoto(appContext, uri, SplashImage.PHOTO_PREFIX)
            }
            if (_binding == null) return@launch
            if (path == null) toast(getString(R.string.bg_photo_failed))
            else select(SplashImage.withNewPhoto(spec, path))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSplashImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spec = SplashImage.load(requireContext())
        //чекбокс нужен, только если фон приложения - фото: у градиента и цвета нечего увеличивать
        val hasBgPhoto = SplashImage.backgroundPhotoPath(requireContext()) != null
        if (!hasBgPhoto && spec.mode == SplashImage.MODE_BACKGROUND) spec = spec.copy(mode = SplashImage.MODE_DEFAULT)
        binding.cbSplashUseBackground.visibility = if (hasBgPhoto) View.VISIBLE else View.GONE
        binding.cbSplashUseBackground.setOnCheckedChangeListener { _, checked ->
            //при переключении в render() флажок ставится кодом: не реагируем на это
            if (checked == (spec.mode == SplashImage.MODE_BACKGROUND)) return@setOnCheckedChangeListener
            select(if (checked) spec.copy(mode = SplashImage.MODE_BACKGROUND)
            else spec.copy(mode = if (spec.photo != null) SplashImage.MODE_CUSTOM else SplashImage.MODE_DEFAULT))
        }
        render()
    }

    //выбор сохраняем сразу: отдельной кнопки «Применить» нет
    private fun select(newSpec: SplashSpec) {
        spec = newSpec
        SplashImage.save(requireContext(), spec)
        render()
    }

    private fun render() {
        val d = resources.displayMetrics.density
        //большое окно: стандартная картинка растягивается, как при запуске, а своя обрезается по центру
        val frame = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = 10 * d; setStroke((2 * d).toInt(), Color.WHITE) }
        binding.ivSplashPreview.background = frame
        val path = SplashImage.resolvePath(requireContext(), spec)
        if (path == null) {
            binding.ivSplashPreview.scaleType = ImageView.ScaleType.FIT_XY
            binding.ivSplashPreview.setImageResource(R.drawable.muz_player_800_1600)
        } else {
            binding.ivSplashPreview.scaleType = ImageView.ScaleType.CENTER_CROP
            val bmp = SplashImage.decode(path, (202 * d).toInt(), (360 * d).toInt())
            if (bmp != null) binding.ivSplashPreview.setImageBitmap(bmp)
            else binding.ivSplashPreview.setImageResource(R.drawable.muz_player_800_1600)
        }
        binding.cbSplashUseBackground.isChecked = spec.mode == SplashImage.MODE_BACKGROUND

        val bgMode = spec.mode == SplashImage.MODE_BACKGROUND
        binding.rowSplash.removeAllViews()
        binding.rowSplash.addView(
            tile(null, R.drawable.muz_player_800_1600, spec.mode == SplashImage.MODE_DEFAULT, getString(R.string.splash_default_label)) {
                select(spec.copy(mode = SplashImage.MODE_DEFAULT))
            }
        )
        binding.rowSplash.addView(tile(null, null, false, "+", big = true) { pickPhoto.launch("image/*") })
        for (p in spec.photos) {
            val bmp = SplashImage.decode(p, (84 * d).toInt(), (132 * d).toInt()) ?: continue
            binding.rowSplash.addView(
                tile(bmp, null, !bgMode && spec.mode == SplashImage.MODE_CUSTOM && spec.photo == p) {
                    select(spec.copy(mode = SplashImage.MODE_CUSTOM, photo = p))
                }
            )
        }
    }

    //плитка, как на экране «Изменить фон»: белая рамка, у выбранной толще и галочка
    private fun tile(
        bitmap: Bitmap?, res: Int?, selected: Boolean, label: String? = null, big: Boolean = false, onClick: () -> Unit
    ): View {
        val d = resources.displayMetrics.density
        val frame = FrameLayout(requireContext())
        frame.layoutParams = LinearLayout.LayoutParams((84 * d).toInt(), (132 * d).toInt()).apply {
            setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
        }
        frame.background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = 8 * d
            setStroke(((if (selected) 3 else 1) * d).toInt().coerceAtLeast(1), Color.WHITE)
        }
        if (bitmap != null || res != null) {
            val iv = ImageView(requireContext())
            iv.scaleType = if (res != null) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.CENTER_CROP
            if (bitmap != null) iv.setImageBitmap(bitmap) else iv.setImageResource(res!!)
            iv.setPadding((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
            frame.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        label?.let {
            val tv = TextView(requireContext())
            tv.text = it
            tv.setTextColor(Color.WHITE)
            tv.setShadowLayer(4f, 0f, 0f, Color.BLACK)
            tv.gravity = if (big) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            tv.textSize = if (big) 36f else 12f
            frame.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        if (selected) {
            val mark = TextView(requireContext())
            mark.text = "✓"
            mark.setTextColor(Color.BLACK)
            mark.setBackgroundColor(Color.WHITE)
            mark.gravity = Gravity.CENTER
            mark.textSize = 14f
            frame.addView(mark, FrameLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt(), Gravity.TOP or Gravity.START))
        }
        frame.setOnClickListener { onClick() }
        return frame
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
