package com.example.muzpleer.ui.theme

import android.graphics.BitmapFactory
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
import com.example.muzpleer.databinding.FragmentBackgroundBinding
import com.example.muzpleer.util.AppBackground
import com.example.muzpleer.util.BgSpec
import com.example.muzpleer.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//экран «Изменить фон»: однотонные цвета, градиенты и своё фото; выбор попадает в предпросмотр экрана темы
class BackgroundFragment : Fragment() {

    private var _binding: FragmentBackgroundBinding? = null
    private val binding get() = _binding!!

    private lateinit var spec: BgSpec

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val path = withContext(Dispatchers.IO) { AppBackground.importPhoto(appContext, uri) }
                if (_binding == null) return@launch
                if (path == null) {
                    toast(getString(R.string.bg_photo_failed))
                } else {
                    //новое фото становится выбранным; если фото уже три, оно заменяет отмеченное или самое старое (по режиму)
                    select(
                        if (AppBackground.isReplaceSelected(appContext))
                            AppBackground.withPhotoReplacingSelected(spec, path)
                        else AppBackground.withNewPhoto(spec, path)
                    )
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun updatePhotoHint() {
        _binding?.tvPhotoMode?.setText(
            if (AppBackground.isReplaceSelected(requireContext())) R.string.bg_mode_replace_selected else R.string.bg_mode_replace_oldest
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spec = AppBackground.pending ?: AppBackground.load(requireContext())
        render()
        //режим замены фото: переключатель под рядом фото
        binding.cbReplaceSelected.isChecked = AppBackground.isReplaceSelected(requireContext())
        binding.cbReplaceSelected.setOnCheckedChangeListener { _, checked ->
            AppBackground.setReplaceSelected(requireContext(), checked)
            updatePhotoHint()
        }
        updatePhotoHint()
    }

    private fun select(newSpec: BgSpec) {
        spec = newSpec
        AppBackground.pending = spec
        //выбранный фон сразу виден на экране, ещё до «Применить»
        activity?.let { AppBackground.applyToWindow(it, spec) }
        render()
    }

    private fun render() {
        binding.rowSolid.removeAllViews()
        for (color in AppBackground.solidColors) {
            val bg = GradientDrawable().apply { setColor(color) }
            val selected = spec.type == AppBackground.TYPE_SOLID && spec.color1 == color
            binding.rowSolid.addView(tile(bg, selected) {
                select(spec.copy(type = AppBackground.TYPE_SOLID, color1 = color))
            })
        }

        binding.rowGradient.removeAllViews()
        //первым идёт стандартный фон приложения
        binding.rowGradient.addView(
            tile(
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF17B8B1.toInt(), 0xFF5C5C5C.toInt())),
                spec.type == AppBackground.TYPE_DEFAULT,
                getString(R.string.bg_default)
            ) { select(spec.copy(type = AppBackground.TYPE_DEFAULT)) }
        )
        for (pair in AppBackground.gradientColors) {
            val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pair)
            val selected = spec.type == AppBackground.TYPE_GRADIENT && spec.color1 == pair[0] && spec.color2 == pair[1]
            binding.rowGradient.addView(tile(bg, selected) {
                select(spec.copy(type = AppBackground.TYPE_GRADIENT, color1 = pair[0], color2 = pair[1]))
            })
        }

        binding.rowPhoto.removeAllViews()
        binding.rowPhoto.addView(
            tile(GradientDrawable().apply { setColor(0xFF000000.toInt()) }, false, "+", big = true) {
                pickPhoto.launch("image/*")
            }
        )
        //загруженные фото идут справа от «+», от старого к новому
        for (path in spec.photos) {
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 }) ?: continue
            binding.rowPhoto.addView(
                tile(null, spec.type == AppBackground.TYPE_PHOTO && spec.photo == path, photo = bitmap) {
                    select(spec.copy(type = AppBackground.TYPE_PHOTO, photo = path))
                }
            )
        }
    }

    //плитка образца: рамка белая, у выбранной толще; подпись и фото необязательны
    private fun tile(
        background: GradientDrawable?,
        selected: Boolean,
        label: String? = null,
        big: Boolean = false,
        photo: android.graphics.Bitmap? = null,
        onClick: () -> Unit
    ): View {
        val d = resources.displayMetrics.density
        val frame = FrameLayout(requireContext())
        frame.layoutParams = LinearLayout.LayoutParams((84 * d).toInt(), (132 * d).toInt()).apply {
            setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
        }
        val shape = (background ?: GradientDrawable().apply { setColor(Color.BLACK) }).apply {
            cornerRadius = 8 * d
            setStroke(((if (selected) 3 else 1) * d).toInt().coerceAtLeast(1), Color.WHITE)
        }
        frame.background = shape
        photo?.let {
            val iv = ImageView(requireContext())
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(it)
            iv.setPadding((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
            frame.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        label?.let {
            val tv = TextView(requireContext())
            tv.text = it
            tv.setTextColor(Color.WHITE)
            tv.gravity = Gravity.CENTER
            tv.textSize = if (big) 36f else 12f
            frame.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        if (selected) {
            //галочка в углу выбранной плитки
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
