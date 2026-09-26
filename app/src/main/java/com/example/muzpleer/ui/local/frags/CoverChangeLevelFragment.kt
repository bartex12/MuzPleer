package com.example.muzpleer.ui.local.frags

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
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
import com.example.muzpleer.databinding.FragmentCoverChangeLevelBinding
import com.example.muzpleer.di.App
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File


//Фрагмент CoverChangeLevelFragment с макетом fragment_cover_change_level -
// экран для замены обложки с выбором места загрузки картинки обложки
// и сохранения в кэш диска и в базу для  альбомов, папок, артистов, плейлистов?

class CoverChangeLevelFragment: Fragment() {
    private var _binding: FragmentCoverChangeLevelBinding? = null
    private val binding get() = _binding!!
    private var isExpanded = false
    private val viewModel: SharedViewModel by activityViewModels()

    private var levelId: Long = -1
    private var levelType: LevelType = LevelType.PLAYLIST
    private var previousTitle: String = "" // Сохраняем предыдущий заголовок

    enum class LevelType {
        PLAYLIST, ALBUM, ARTIST, FOLDER
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                Log.d(TAG, "1---CoverChangeLevelFragment registerForActivityResult uri = $it ")
                viewModel.updateCoverImageLevel(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Получаем аргументы
        levelId = arguments?.getLong("levelId") ?: -1
        levelType = arguments?.getSerializable("levelType") as? LevelType ?: LevelType.PLAYLIST
        Log.d(TAG, "2---CoverChangeLevelFragment onCreate levelId = $levelId levelType = $levelType")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoverChangeLevelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сохраняем текущий заголовок перед сменой
        previousTitle = (requireActivity() as? MainActivity)?.getCurrentTitle() ?: "Музыка на ладони"
        Log.d(TAG, "5*** CoverChangeLevelFragment onViewCreated previousTitle = $previousTitle")

        //обеспечивает установку обложки при открытии CoverChangeLevelFragment и замене обложки через pickImage
        viewModel.coverImageUriLevel.observe(viewLifecycleOwner) { uri ->
            Log.d(TAG, "3--- CoverChangeLevelFragment coverImageUriLevel.observe: uri = $uri ")

            uri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.coverImageViewLevel)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.coverImageViewLevel)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.coverImageViewLevel)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CoverChangeLevelFragment exception when loading: ${e.message}")
                    binding.coverImageViewLevel.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.coverImageViewLevel.setImageResource(R.drawable.muz_player3)
        }

        binding.usePhonePhotosLevel.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.searchOnlineLevel.setOnClickListener {
            openFlickrSearch()
        }

        fun getDefaultCoverUri(): Uri {
            return "android.resource://${(App.instance).packageName}/${R.drawable.muz_player3}".toUri()
        }

        binding.restoreDefaultLevel.setOnClickListener {
            val uri:Uri = getDefaultCoverUri()
           when(levelType){
                LevelType.PLAYLIST ->{ viewModel.updateCoverImageLevelAndSave(uri, levelId)}
                LevelType.ALBUM ->{viewModel.updateCoverImageAlbumAndSave(uri, levelId)}
                LevelType.ARTIST ->{viewModel.updateCoverImageArtistsAndSave(uri, levelId)}
                LevelType.FOLDER ->{viewModel.updateCoverImageFolderAndSave(uri, levelId)}
            }
        }

        binding.saveButtonLevel.setOnClickListener {
            viewModel.getCoverImageUriLevel()?. let{curUri->
                Log.d(TAG, "4--- CoverChangeLevelFragment saveButtonLevel: curUri = $curUri")
                when (levelType) {
                    LevelType.PLAYLIST -> viewModel.updateCoverImageLevelAndSave(curUri, levelId) //+
                    LevelType.ALBUM -> { viewModel.updateCoverImageAlbumAndSave(curUri, levelId) }
                    LevelType.ARTIST -> { viewModel.updateCoverImageArtistsAndSave(curUri, levelId) } //+
                    LevelType.FOLDER -> { viewModel.updateCoverImageFolderAndSave(curUri, levelId) } //+
                }
            }
            findNavController().navigateUp() // переходим  назад, откуда пришли
        }

        binding.coverImageViewLevel.setOnClickListener {
            isExpanded = !isExpanded
            //
            TransitionManager.beginDelayedTransition(
                binding.coverContainer, TransitionSet()
                    .addTransition(ChangeBounds())
                    .addTransition(ChangeImageTransform())
            )
            val params = binding.coverImageViewLevel.layoutParams as ConstraintLayout.LayoutParams
            if (isExpanded) {
                // Оставляем существующие constraints, но меняем размеры
                params.topMargin = 0
                params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                params.dimensionRatio = ""
                binding.coverImageViewLevel.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                // Возвращаем оригинальные размеры
                params.topMargin = 16
                params.width = 0
                params.height = 0
                params.dimensionRatio = "1:1"
                params.matchConstraintPercentWidth = 0.6f
                binding.coverImageViewLevel.scaleType = ImageView.ScaleType.FIT_CENTER
            }
            binding.coverImageViewLevel.layoutParams = params
        }
    }

    override fun onResume() {
        super.onResume()
        // Устанавливаем заголовок
        updateToolbarTitle("Замена обложки")
    }

    override fun onDestroyView() {
        // Восстанавливаем предыдущий заголовок при уходе с фрагмента
        restorePreviousTitle()
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

    private fun openFlickrSearch() {
        val intent = Intent(Intent.ACTION_VIEW, "https://www.flickr.com/search/".toUri())
        startActivity(intent)
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
                    // крэш, нужно binding вводить в функцию, как сделано в PlaylistsSelectionAdapter
                   // binding.coverImageViewLevel.setImageResource(R.drawable.muz_player3)
                    Log.d(TAG, "❌CoverChangeLevelFragment Glide load failed for URI: $artUri? ${e?.message}")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅ CoverChangeLevelFragment Glide load success for URI: $artUri")
                    Log.d(TAG, "✅ CoverChangeLevelFragment DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

}