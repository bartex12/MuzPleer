package com.example.muzpleer.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.muzpleer.util.LyricsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private var isExpanded = false
    private var previousTitle: String = "" // Сохраняем предыдущий заголовок
    private var showLyrics = false          // true - открыта страница «Текст песни»
    private var lyricsJob: kotlinx.coroutines.Job? = null

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

        showLyrics = savedInstanceState?.getBoolean(STATE_LYRICS) ?: false
        setupTopBar()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LYRICS, showLyrics)
    }

    //Верхняя панель: очередь (заглушка) | Песня | Текст песни | меню (заглушка)
    private fun setupTopBar() {
        binding.tabQueue.setOnClickListener { stub("Очередь воспроизведения") }
        binding.tabSong.setOnClickListener { showPage(lyrics = false) }
        binding.tabLyrics.setOnClickListener { showPage(lyrics = true) }
        binding.btnLyricsFont.setOnClickListener { showFontSizeDialog() }
        binding.tvLyrics.textSize = FONT_SIZES_SP[fontSizeIndex()]
        binding.btnPlayerMenu.setOnClickListener {
            stub(if (showLyrics) "Меню текста песни" else "Меню песни")
        }
        showPage(showLyrics)
    }

    private fun fontSizeIndex(): Int =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getInt(PREF_LYRICS_FONT, DEFAULT_FONT_INDEX).coerceIn(0, FONT_SIZES_SP.lastIndex)

    //Окно выбора размера шрифта текста: два размера меньше обычного и два больше
    private fun showFontSizeDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_font_size, null)
        val group = dialogView.findViewById<RadioGroup>(R.id.rgFontSize)
        val ids = intArrayOf(R.id.rbFont0, R.id.rbFont1, R.id.rbFont2, R.id.rbFont3, R.id.rbFont4)
        group.check(ids[fontSizeIndex()])
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        group.setOnCheckedChangeListener { _, checkedId ->
            val index = ids.indexOf(checkedId).takeIf { it >= 0 } ?: return@setOnCheckedChangeListener
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putInt(PREF_LYRICS_FONT, index).apply()
            _binding?.tvLyrics?.textSize = FONT_SIZES_SP[index]
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun stub(name: String) {
        Toast.makeText(requireContext(), "$name — пока в разработке", Toast.LENGTH_SHORT).show()
    }

    //Переключение страниц «Песня» / «Текст песни» и подсветка выбранной вкладки
    private fun showPage(lyrics: Boolean) {
        showLyrics = lyrics
        binding.pageSong.visibility = if (lyrics) View.GONE else View.VISIBLE
        binding.pageLyrics.visibility = if (lyrics) View.VISIBLE else View.GONE
        val selected = ContextCompat.getColor(requireContext(), R.color.white)
        val normal = (selected and 0x00FFFFFF) or (0x99 shl 24)
        binding.tvSongLabel.setTextColor(if (lyrics) normal else selected)
        binding.tvSongLabel.setTypeface(null, if (lyrics) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
        binding.tabSongIcon.alpha = if (lyrics) 0.6f else 1f
        binding.tvLyricsLabel.setTextColor(if (lyrics) selected else normal)
        binding.tvLyricsLabel.setTypeface(null, if (lyrics) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabLyricsIcon.alpha = if (lyrics) 1f else 0.6f
        binding.tvQueueLabel.setTextColor(normal)
        binding.tabQueueIcon.alpha = 0.6f
    }

    //Читаем текст песни из тега файла и показываем на странице «Текст песни»
    private fun loadLyrics(song: Song) {
        binding.tvLyricsTitle.text = song.title
        lyricsJob?.cancel()
        lyricsJob = viewLifecycleOwner.lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val text = withContext(Dispatchers.IO) {
                LyricsReader.read(appContext, song.id, song.getContentUri())
            }
            _binding ?: return@launch
            binding.btnLyricsFont.visibility = if (text != null) View.VISIBLE else View.INVISIBLE
            if (text != null) {
                binding.tvLyrics.text = text
                binding.tvLyrics.gravity = android.view.Gravity.START
                binding.tvLyrics.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                binding.tvLyrics.alpha = 1f
            } else {
                binding.tvLyrics.text = "Текста песни в файле нет"
                binding.tvLyrics.gravity = android.view.Gravity.CENTER
                binding.tvLyrics.textAlignment = View.TEXT_ALIGNMENT_CENTER
                binding.tvLyrics.alpha = 0.6f
            }
            binding.lyricsScroll.scrollTo(0, 0)
        }
    }

    private fun setupControls() {
        binding.playPauseButton.setOnClickListener {
            playerViewModel.togglePlayPause()
        }

        binding.previousButton.setOnClickListener {
            // Переход к предыдущему треку
            playerViewModel.playPrevious()
        }

        binding.nextButton.setOnClickListener {
            // Переход к следующему треку
            playerViewModel.playNext()
        }

        binding.rewindBackButton.setOnClickListener {
            playerViewModel.seekRelative(-5000) // -5 секунд
        }
        binding.rewindForwardButton.setOnClickListener {
            playerViewModel.seekRelative(15000) // +15 секунд
        }

        // Новые кнопки перемотки
        binding.btnRewind30.setOnClickListener {
            playerViewModel.seekRelative(-30000) // -30 секунд
        }
        binding.btnRewind10.setOnClickListener {
            playerViewModel.seekRelative(-10000) // -10 секунд
        }
        binding.btnRewind5.setOnClickListener {
            playerViewModel.seekRelative(-5000) // -5 секунд
        }
        binding.btnForward5.setOnClickListener {
            playerViewModel.seekRelative(5000) // +5 секунд
        }
        binding.btnForward10.setOnClickListener {
            playerViewModel.seekRelative(10000) // +10 секунд
        }
        binding.btnForward30.setOnClickListener {
            playerViewModel.seekRelative(30000) // +30 секунд
        }


        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playerViewModel.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.artworkImageView.setOnClickListener {
            isExpanded = !isExpanded
            //
            TransitionManager.beginDelayedTransition(
                binding.pageSong, TransitionSet()
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
            //подпись вкладки «очередь»: номер трека из общего числа
            val position = songAndPlaylist.playlist.indexOfFirst { it.mediaUri == currentSong?.mediaUri }
            binding.tvQueueLabel.text = "#${position + 1}/${songAndPlaylist.playlist.size}"

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

        playerViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.playPauseButton.setImageResource(
                if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white
            )
        }

        playerViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            binding.tvCurrentTime.text = formatTime(position)
            binding.seekBar.progress = position.toInt()
        }

        playerViewModel.duration.observe(viewLifecycleOwner) { duration ->
            binding.tvTotalTime.text = formatTime(duration)
            binding.seekBar.max = duration.toInt()
            //при открытии экрана позиция приходит раньше длительности и обрезается по старому max,
            //а на паузе новых значений нет - поэтому позицию выставляем заново после смены max
            playerViewModel.currentPosition.value?.let { binding.seekBar.progress = it.toInt() }
        }

        playerViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                playerViewModel.clearError()
            }
        }
    }

    private fun updateUI(song: Song) {
        loadLyrics(song)
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
        private const val STATE_LYRICS = "state_lyrics"
        private const val PREF_LYRICS_FONT = "lyrics_font_index"
        private const val DEFAULT_FONT_INDEX = 2
        //размеры шрифта текста песни, sp: два меньше обычного (20) и два больше
        private val FONT_SIZES_SP = floatArrayOf(14f, 17f, 20f, 24f, 28f)
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
        playerViewModel.setPlayerVisibility(false)
    }

    private fun showActivityPlayer() {
        playerViewModel.setPlayerVisibility(true)
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
