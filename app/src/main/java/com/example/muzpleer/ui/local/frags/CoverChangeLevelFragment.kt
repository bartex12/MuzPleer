package com.example.muzpleer.ui.local.frags

import android.content.Context
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import com.example.muzpleer.databinding.FragmentCoverChangeBinding
import com.example.muzpleer.di.App
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.AlbumsViewModel
import com.example.muzpleer.ui.local.viewmodel.ArtistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File


//Фрагмент CoverChangeLevelFragment с макетом fragment_cover_change -
// экран для замены обложки с выбором места загрузки картинки обложки
// и сохранения в кэш диска и в базу для  альбомов, папок, артистов, плейлистов?

class CoverChangeLevelFragment: Fragment() {
    private var _binding: FragmentCoverChangeBinding? = null
    private val binding get() = _binding!!
    private var isExpanded = false
    private val viewModel: SharedViewModel by activityViewModels()
    private val albumsViewModel: AlbumsViewModel by activityViewModel()
    private val artistsViewModel: ArtistsViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()

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
                openCrop(it)
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
        _binding = FragmentCoverChangeBinding.inflate(inflater, container, false)
        return binding.root
    }

    //для альбомов: показываем новую обложку и сохраняем её в базу
    private fun saveAlbumCover(uri: Uri, albumId: Long, onDone: () -> Unit = {}) {
        viewModel.setCoverImageUriLevel(uri)
        albumsViewModel.saveCoverAlbumToDatabase(requireContext(), uri, albumId, onDone)
    }

    //для исполнителей: показываем новую обложку и сохраняем её в базу
    private fun saveArtistCover(uri: Uri, artistId: Long, onDone: () -> Unit = {}) {
        viewModel.setCoverImageUriLevel(uri)
        artistsViewModel.saveCoverArtistToDatabase(requireContext(), uri, artistId, onDone)
    }

    //для папок: показываем новую обложку и сохраняем её в базу
    private fun saveFolderCover(uri: Uri, folderId: Long, onDone: () -> Unit = {}) {
        viewModel.setCoverImageUriLevel(uri)
        foldersViewModel.saveCoverFolderToDatabase(requireContext(), uri, folderId, onDone)
    }

    //для плейлистов: показываем новую обложку и сохраняем её в базу
    private fun savePlaylistCover(uri: Uri, playlistId: Long, onDone: () -> Unit = {}) {
        viewModel.setCoverImageUriLevel(uri)
        playlistsViewModel.saveCoverPlaylistToDatabase(requireContext(), uri, playlistId, onDone)
    }

    //после выбора фото открываем экран редактирования: пользователь рамкой выбирает кусок для обложки
    private fun openCrop(uri: Uri) {
        findNavController().navigate(
            R.id.cropCoverFragment,
            android.os.Bundle().apply {
                putString(CropCoverFragment.ARG_URI, uri.toString())
                putBoolean(CropCoverFragment.ARG_LEVEL, true)
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
        Log.d(TAG, "5*** CoverChangeLevelFragment onViewCreated previousTitle = $previousTitle")

        //обеспечивает установку обложки при открытии CoverChangeLevelFragment и замене обложки через pickImage
        viewModel.coverImageUriLevel.observe(viewLifecycleOwner) { uri ->
            updateRestoreAvailability(uri)
            Log.d(TAG, "3--- CoverChangeLevelFragment coverImageUriLevel.observe: uri = $uri ")

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
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.coverImageView)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CoverChangeLevelFragment exception when loading: ${e.message}")
                    binding.coverImageView.setImageResource(R.drawable.muz_player3)
                }
            }?: run {
                //обложки нет: отменяем незавершённую загрузку прежней картинки, иначе она появится поверх заглушки
                Glide.with(binding.root.context).clear(binding.coverImageView)
                binding.coverImageView.setImageResource(R.drawable.muz_player3)
            }
        }

        binding.usePhonePhotos.setOnClickListener {
            pickImage.launch("image/*")
        }

        //поиск картинки в интернете: в запрос подставляем название альбома/исполнителя/папки/плейлиста
        val query = when (levelType) {
            LevelType.ALBUM -> albumsViewModel.albums.value
                ?.find { it.id == levelId || it.albumId == levelId }
                ?.let { "${it.artist} ${it.title}" }
            LevelType.ARTIST -> artistsViewModel.artists.value
                ?.find { it.id == levelId || it.artistId == levelId }?.name
            LevelType.FOLDER -> foldersViewModel.folders.value?.find { it.id == levelId }?.name
            LevelType.PLAYLIST -> playlistsViewModel.playlists.value
                ?.find { it.id == levelId }?.playlistName
        } ?: previousTitle
        com.example.muzpleer.util.CoverWebSearch.setup(binding.webSearch.chipsWebSearch, query)

        binding.saveButton.setOnClickListener {
            //повторное нажатие блокируем; назад уходим после записи в базу, чтобы список показал новую обложку
            binding.saveButton.isEnabled = false
            val goBack = { if (_binding != null) findNavController().navigateUp() }
            val curUri = viewModel.getCoverImageUriLevel()
            if (curUri == null) {
                goBack()
            } else {
                Log.d(TAG, "4--- CoverChangeLevelFragment saveButton: curUri = $curUri")
                when (levelType) {
                    LevelType.PLAYLIST -> savePlaylistCover(curUri, levelId, goBack)
                    LevelType.ALBUM -> saveAlbumCover(curUri, levelId, goBack)
                    LevelType.ARTIST -> saveArtistCover(curUri, levelId, goBack)
                    LevelType.FOLDER -> saveFolderCover(curUri, levelId, goBack)
                }
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
        if (item.itemId == R.id.action_restore_cover) {
            //сначала спрашиваем: показываем, какое изображение вернётся
            com.example.muzpleer.ui.local.helper.showRestoreCoverDialog(requireContext(), viewModel.getCoverImageUriLevel()) {
                if (_binding == null) return@showRestoreCoverDialog
                val uri: Uri = "android.resource://${(App.instance).packageName}/${R.drawable.muz_player3}".toUri()
                when (levelType) {
                    LevelType.PLAYLIST -> savePlaylistCover(uri, levelId)
                    LevelType.ALBUM -> saveAlbumCover(uri, levelId)
                    LevelType.ARTIST -> saveArtistCover(uri, levelId)
                    LevelType.FOLDER -> saveFolderCover(uri, levelId)
                }
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
                    // крэш, нужно binding вводить в функцию, как сделано в PlaylistsSelectionAdapter
                   // binding.coverImageView.setImageResource(R.drawable.muz_player3)
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