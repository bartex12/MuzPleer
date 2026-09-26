package com.example.muzpleer.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
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
import com.example.muzpleer.databinding.FragmentPlayerBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.io.File

class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private var isExpanded = false
    private var previousTitle: String = "" // Сохраняем предыдущий заголовок

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сохраняем текущий заголовок перед сменой
        previousTitle = (requireActivity() as? MainActivity)?.getCurrentTitle() ?: "Музыка на ладони"
        Log.d(TAG, "=== === PlayerFragment onViewCreated previousTitle = $previousTitle")

        setupControls()
        observeViewModel()

        // Скрыть плеер при открытии фрагмента
        hideActivityPlayer()
    }

    override fun onResume() {
        super.onResume()
        // Устанавливаем заголовок
        updateToolbarTitle("Аудиоплеер")
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    private fun setupControls() {
        binding.playPauseButton.setOnClickListener {
            viewModel.togglePlayPause()
        }

        binding.previousButton.setOnClickListener {
            // Переход к предыдущему треку
            viewModel.playPrevious()
        }

        binding.nextButton.setOnClickListener {
            // Переход к следующему треку
            viewModel.playNext()
        }

        binding.rewindBackButton.setOnClickListener {
            viewModel.seekRelative(-5000) // -5 секунд
        }
        binding.rewindForwardButton.setOnClickListener {
            viewModel.seekRelative(15000) // +15 секунд
        }

        // Новые кнопки перемотки
        binding.btnRewind30.setOnClickListener {
            viewModel.seekRelative(-30000) // -30 секунд
        }
        binding.btnRewind10.setOnClickListener {
            viewModel.seekRelative(-10000) // -10 секунд
        }
        binding.btnRewind5.setOnClickListener {
            viewModel.seekRelative(-5000) // -5 секунд
        }
        binding.btnForward5.setOnClickListener {
            viewModel.seekRelative(5000) // +5 секунд
        }
        binding.btnForward10.setOnClickListener {
            viewModel.seekRelative(10000) // +10 секунд
        }
        binding.btnForward30.setOnClickListener {
            viewModel.seekRelative(30000) // +30 секунд
        }


        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.artworkImageView.setOnClickListener {
            isExpanded = !isExpanded
            //
            TransitionManager.beginDelayedTransition(
                binding.playerContainer, TransitionSet()
                    .addTransition(ChangeBounds())
                    .addTransition(ChangeImageTransform())
            )
            val params = binding.artworkImageView.layoutParams as ConstraintLayout.LayoutParams
            if (isExpanded) {
                // Оставляем существующие constraints, но меняем размеры
                params.topMargin = 0
                params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                params.dimensionRatio = ""
                binding.artworkImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                // Возвращаем оригинальные размеры
                params.topMargin = 16
                params.width = 0
                params.height = 0
                params.dimensionRatio = "1:1"
                params.matchConstraintPercentWidth = 0.6f
                binding.artworkImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            }

            binding.artworkImageView.layoutParams = params
        }
    }

    private fun observeViewModel() {

        viewModel.songAndPlaylist.observe(viewLifecycleOwner) { songAndPlaylist ->
            Log.d(TAG, "1*** PlayerFragment onViewCreated songAndPlaylist.observe: " +
                    " currentSong title = ${songAndPlaylist.song.title} " +
                    " currentSong.artUri = ${songAndPlaylist.song.artUri}" +
                    " playlist.size = ${songAndPlaylist.playlist.size}")
            val currentSong = viewModel.getCurrentSong()
            Log.d(TAG, "2*** PlayerFragment onViewCreated songAndPlaylist.observe: " +
                    " currentSong title = ${currentSong?.title}  " +
                    " currentSong.artUri = ${currentSong?.artUri}")

            binding.tvTitle.text = currentSong?.title
            binding.tvArtist.text =currentSong?.artist

            //находим индекс трека в плейлисте
            val indexOfTrack =
                songAndPlaylist.playlist.indexOfFirst {song->
                    song.mediaUri == currentSong?.mediaUri
                }
            //перед передачей в Handler меняем в передаваемом плейлисте artUri для текущего трека
            songAndPlaylist.playlist[indexOfTrack].artUri =currentSong?.artUri
            val trackArtUri =  songAndPlaylist.playlist[indexOfTrack].artUri
            Log.d(TAG, "3*** PlayerFragment onViewCreated indexOfTrack = $indexOfTrack " +
                    "songAndPlaylist.playlist.size = ${songAndPlaylist.playlist.size} artUri = $trackArtUri")

            trackArtUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri)){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.artworkImageView)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.artworkImageView)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri), binding.artworkImageView)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ PlayerFragment observeViewModel exception when loading: ${e.message}")
                    binding.artworkImageView.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.artworkImageView.setImageResource(R.drawable.muz_player3)

        }

        // ✅ НОВЫЙ НАБЛЮДАТЕЛЬ: следим за изменением текущей песни
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                Log.d(TAG, "5*** PlayerFragment currentSong.observe song.title: ${song.title}")
                updateUI(song)
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.playPauseButton.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        viewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            binding.tvCurrentTime.text = formatTime(position)
            binding.seekBar.progress = position.toInt()
        }

        viewModel.duration.observe(viewLifecycleOwner) { duration ->
            binding.tvTotalTime.text = formatTime(duration)
            binding.seekBar.max = duration.toInt()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun updateUI(song: Song) {
        binding.tvTitle.text = song.title
        binding.tvArtist.text = song.artist

        song.artUri?.let{artUri->
            // Загружаем изображение
            try {
                if (isContentProviderUri(artUri)){
                    // Загрузка обложки из  content:/com.android.providers.downloads
                    showImageWithGlide(binding.root.context, artUri, binding.artworkImageView)
                }else  if (isContentProviderUriPicker(artUri.toString())){
                    // Загрузка обложки из picker
                    val  photoPickerUri =artUri.toString().toUri()
                    showImageWithGlide(binding.root.context, photoPickerUri, binding.artworkImageView)
                }else{
                    // Загрузка обложки из кэша приложения
                    showImageWithGlide(binding.root.context, File(artUri), binding.artworkImageView)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌PlayerFragment updateUI exception when loading: ${e.message}")
                binding.artworkImageView.setImageResource(R.drawable.muz_player3)
            }
        }?: binding.artworkImageView.setImageResource(R.drawable.muz_player3)
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        // Восстанавливаем предыдущий заголовок при уходе с фрагмента
        restorePreviousTitle()
        // Показать нижний плеер при закрытии фрагмента
        showActivityPlayer()
        _binding = null
        super.onDestroyView()
    }

    private fun restorePreviousTitle() {
        if (previousTitle.isNotEmpty()) {
            (requireActivity() as? MainActivity)?.updateToolbarTitle(previousTitle)
        }
    }

    companion object {
        const val TAG = "33333"
        private const val ARG_TRACK = "track"
        private const val ARG_PLAYLIST = "playlist"

        fun newInstance(track: Song, playlist: List<Song>): PlayerFragment {
            return PlayerFragment().apply {
                arguments = bundleOf(
                    ARG_TRACK to track,
                    ARG_PLAYLIST to ArrayList(playlist))
            }
        }
    }

    private fun hideActivityPlayer() {
        viewModel.setPlayerVisibility(false)
    }

    private fun showActivityPlayer() {
        viewModel.setPlayerVisibility(true)
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
                    Log.e(TAG, " ❌PlayerFragment Glide load failed for URI: $artUri", e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅PlayerFragment Glide load success for URI: $artUri")
                    Log.d(TAG, "✅PlayerFragment DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}
