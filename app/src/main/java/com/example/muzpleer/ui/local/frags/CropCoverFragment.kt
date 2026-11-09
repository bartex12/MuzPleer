package com.example.muzpleer.ui.local.frags

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.muzpleer.MainActivity
import com.example.muzpleer.databinding.FragmentCropCoverBinding
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Экран «Редактировать фото»: после выбора картинки пользователь рамкой выбирает
 * нужный кусок, он и становится обложкой. Результат - квадратный JPEG в кэше приложения,
 * его uri отдаётся во ViewModel так же, как раньше отдавался выбранный файл.
 */
class CropCoverFragment : Fragment() {
    private var _binding: FragmentCropCoverBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModels()

    private var saveItem: MenuItem? = null
    private var busy = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropCoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        if (!binding.cropView.hasBitmap()) loadSource()
    }

    private fun loadSource() {
        val src = requireArguments().getString(ARG_URI)?.toUri() ?: return failAndExit()
        binding.cropProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { decode(src) }
            if (_binding == null) return@launch
            binding.cropProgress.visibility = View.GONE
            if (bmp == null) failAndExit() else {
                binding.cropView.setBitmap(bmp)
                updateSaveEnabled()
            }
        }
    }

    private fun failAndExit() {
        Toast.makeText(requireContext(), "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    /** Читает картинку с уменьшением до ~2048 px по длинной стороне и поворотом по EXIF. */
    private fun decode(uri: Uri): Bitmap? = try {
        val cr = requireContext().applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        if (raw == null) null else applyExif(raw, orientation)
    } catch (e: Throwable) {
        null
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun updateSaveEnabled() {
        saveItem?.isEnabled = !busy && _binding?.cropView?.hasBitmap() == true
        saveItem?.icon?.alpha = if (saveItem?.isEnabled == true) 255 else 100
    }

    private fun saveCrop() {
        if (busy || _binding == null) return
        val out = binding.cropView.cropBitmap(OUT_SIZE) ?: return
        busy = true
        updateSaveEnabled()
        binding.cropProgress.visibility = View.VISIBLE
        val ctx = requireContext().applicationContext
        val isLevel = requireArguments().getBoolean(ARG_LEVEL)
        //viewModel берём заранее: после ухода с экрана viewLifecycleOwner уже не работает
        val vm = viewModel
        val nav = findNavController()
        viewLifecycleOwner.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val dir = File(ctx.cacheDir, "crops").apply { mkdirs() }
                    val f = File(dir, "cover_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    f
                } catch (e: Exception) { null }
            }
            if (_binding == null) return@launch
            busy = false
            binding.cropProgress.visibility = View.GONE
            updateSaveEnabled()
            if (file == null) {
                Toast.makeText(ctx, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = Uri.fromFile(file)
            if (isLevel) vm.updateCoverImageLevel(uri) else vm.updateCoverImage(uri)
            nav.navigateUp()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(com.example.muzpleer.R.menu.menu_crop_cover, menu)
        saveItem = menu.findItem(com.example.muzpleer.R.id.action_save_crop)
        updateSaveEnabled()
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == com.example.muzpleer.R.id.action_save_crop) {
            saveCrop()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? MainActivity)?.updateToolbarTitle("Редактировать фото")
    }

    override fun onDestroyView() {
        //возвращаем заголовок экрана, с которого пришли
        requireArguments().getString(ARG_TITLE)?.takeIf { it.isNotEmpty() }?.let {
            (activity as? MainActivity)?.updateToolbarTitle(it)
        }
        saveItem = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_URI = "uri"
        const val ARG_LEVEL = "level"
        const val ARG_TITLE = "title"
        private const val MAX_SIDE = 2048
        private const val OUT_SIZE = 800
    }
}
