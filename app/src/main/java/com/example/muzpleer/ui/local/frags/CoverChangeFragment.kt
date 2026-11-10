package com.example.muzpleer.ui.local.frags

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import com.example.muzpleer.databinding.FragmentCoverChangeBinding
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

class CoverChangeFragment : Fragment() {
    private var _binding: FragmentCoverChangeBinding? = null
    private val binding get() = _binding!!
    private var isExpanded = false
    private var previousTitle: String = "" // Сохраняем предыдущий заголовок
    private val viewModel: SharedViewModel by activityViewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Log.d(TAG, "!!!CoverChangeFragment registerForActivityResult uri = $it ")
            openCrop(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoverChangeBinding.inflate(inflater, container, false)
        return binding.root
    }

    //после выбора фото открываем экран редактирования: пользователь рамкой выбирает кусок для обложки
    private fun openCrop(uri: Uri) {
        findNavController().navigate(
            R.id.cropCoverFragment,
            android.os.Bundle().apply {
                putString(CropCoverFragment.ARG_URI, uri.toString())
                putBoolean(CropCoverFragment.ARG_LEVEL, false)
                putString(CropCoverFragment.ARG_TITLE, previousTitle)
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        // Сохраняем текущий заголовок перед сменой
        //при возврате с экрана редактирования фото заголовок уже известен - не затираем его
        if (previousTitle.isEmpty()) previousTitle = (requireActivity() as? MainActivity)?.getCurrentTitle() ?: "Музыка на ладони"
        Log.d(TAG, "5*** CoverChangeFragment onViewCreated previousTitle = $previousTitle")

        //обеспечивает установку обложки при открытии CoverChangeFragment и замене обложки через pickImage
        viewModel.coverImageUri.observe(viewLifecycleOwner) { uri ->
            updateRestoreAvailability(uri)
            Log.d(TAG, "4*** CoverChangeFragment coverImageUri.observe: uri = $uri ")

            uri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.coverImageView)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.coverImageView)
                    }else if (artUri.toString().contains("://")){
                        // любой другой uri (галерея MIUI, content://media/..., file://, android.resource://)
                        showImageWithGlide(binding.root.context, artUri, binding.coverImageView)
                    }else {
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.coverImageView)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CoverChangeFragment exception when loading: ${e.message}")
                    binding.coverImageView.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.coverImageView.setImageResource(R.drawable.muz_player3)
        }

        binding.usePhonePhotos.setOnClickListener {
            pickImage.launch("image/*")
        }

        //поиск картинки в интернете: в запрос подставляем исполнителя и название выбранной песни
        val song = viewModel.getSelectedSong()
        val query = song?.let { "${it.artist.takeUnless { a -> a.isBlank() || a == "<unknown>" }.orEmpty()} ${it.title}".trim() }.orEmpty()
        com.example.muzpleer.util.CoverWebSearch.setup(binding.webSearch.chipsWebSearch, query) {
            findNavController().navigate(
                R.id.generateCoverFragment,
                android.os.Bundle().apply {
                    putBoolean(GenerateCoverFragment.ARG_LEVEL, false)
                    putString(GenerateCoverFragment.ARG_TITLE, previousTitle)
                    putInt(GenerateCoverFragment.ARG_POP_TO, R.id.coverChangeFragment)
                }
            )
        }

        binding.saveButton.setOnClickListener {
            //назад уходим после записи в базу: список, на который вернёмся, читает песни из базы
            binding.saveButton.isEnabled = false
            val curUri = viewModel.getCoverImageUri()
            val goBack = { if (_binding != null) findNavController().navigateUp() }
            if (curUri != null) {
                Log.d(TAG, "1*** CoverChangeFragment saveButton: curUri = $curUri")
                viewModel.updateCoverImageAndSave(curUri, goBack)
            } else {
                goBack()
            }
        }

        binding.coverImageView.setOnClickListener {
            //PhotoView присылает одиночное касание с задержкой: экран к этому моменту мог уже закрыться
            val b = _binding ?: return@setOnClickListener
            isExpanded = !isExpanded
            //
            TransitionManager.beginDelayedTransition(
                b.coverContainer, TransitionSet()
                    .addTransition(ChangeBounds())
                    .addTransition(ChangeImageTransform())
            )
            val params = b.coverImageView.layoutParams as ConstraintLayout.LayoutParams
            if (isExpanded) {
                // Оставляем существующие constraints, но меняем размеры
                params.topMargin = 0
                params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                //на всю видимую область прокручиваемой части
                params.height = b.coverScroll.height
                params.dimensionRatio = ""
                b.coverImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                // Возвращаем оригинальные размеры
                params.topMargin = 0
                val size = (280 * resources.displayMetrics.density).toInt()
                params.width = size
                params.height = size
                params.dimensionRatio = null
                b.coverImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            }

            b.coverImageView.layoutParams = params
        }
    }

    private var restoreItem: android.view.MenuItem? = null
    private var coverIsDefault = false  //на экране уже картинка по умолчанию: возвращаться не к чему

    //значок «Вернуться к изображению по умолчанию» скрываем, пока на экране картинка по умолчанию
    private fun updateRestoreAvailability(uri: Uri?) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val isDefault = withContext(Dispatchers.IO) { com.example.muzpleer.util.isDefaultCover(appContext, uri) }
            coverIsDefault = isDefault
            restoreItem?.isVisible = !isDefault
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        inflater.inflate(R.menu.menu_restore_cover, menu)
        restoreItem = menu.findItem(R.id.action_restore_cover)
        restoreItem?.isVisible = !coverIsDefault
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_cover_hint) {
            com.example.muzpleer.ui.local.helper.showCoverHintDialog(requireContext())
            return true
        }
        if (item.itemId == R.id.action_restore_cover) {
            //сначала спрашиваем: показываем, какое изображение вернётся
            com.example.muzpleer.ui.local.helper.showRestoreCoverDialog(requireContext(), viewModel.getCoverImageUri()) {
                if (_binding != null) viewModel.restoreDefaultCover()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        // Устанавливаем заголовок
        updateToolbarTitle("Замена обложки")
    }

    override fun onDestroyView() {
        // Восстанавливаем предыдущий заголовок при уходе с фрагмента
        restorePreviousTitle()
        restoreItem = null
        _binding = null
        super.onDestroyView()
    }

    private fun restorePreviousTitle() {
        if (previousTitle.isNotEmpty()) {
            (requireActivity() as? MainActivity)?.updateToolbarTitle(previousTitle)
        }
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    companion object{
        const val  TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView){
        // Загрузка обложки
        Glide.with(context)
            .load(artUri)
            .placeholder(R.drawable.muz_player3)
            .error(R.drawable.muz_player3)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable?>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "❌MainActivity Glide load failed for URI: $artUri")
                    imageView.setImageResource(R.drawable.muz_player3)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅CoverChangeFragment Glide load success for URI: $artUri")
                    Log.d(TAG, "✅CoverChangeFragment DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

}