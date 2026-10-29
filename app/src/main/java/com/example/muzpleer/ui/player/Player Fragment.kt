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
import android.widget.PopupWindow
import android.widget.TextView
import android.view.Gravity
import androidx.navigation.fragment.findNavController
import com.example.muzpleer.util.shareSong
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.model.SourceType
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.helper.PreferenceHelperImpl
import com.example.muzpleer.ui.local.helper.showTimeLimitDialog
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
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
    //кнопка «Назад» на страницах «Очередь» и «Текст песни» сначала возвращает на страницу «Песня»
    private val backToSongCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showPage(PAGE_SONG)
        }
    }
    private var page = PAGE_SONG            // открытая страница: очередь / песня / текст песни
    private lateinit var queueAdapter: QueueAdapter
    private lateinit var queueTouchHelper: ItemTouchHelper
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val appPrefs by lazy { PreferenceHelperImpl(requireActivity().application) }
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

        page = savedInstanceState?.getInt(STATE_PAGE) ?: PAGE_SONG
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backToSongCallback)
        setupQueue()
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
        syncQueueWithPlaylist()
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, page)
    }

    //Верхняя панель: очередь | Песня | Текст песни | меню (своё на каждой странице)
    private fun setupTopBar() {
        binding.tabQueue.setOnClickListener { showPage(PAGE_QUEUE) }
        binding.tabSong.setOnClickListener { showPage(PAGE_SONG) }
        binding.tabLyrics.setOnClickListener { showPage(PAGE_LYRICS) }
        binding.btnLyricsFont.setOnClickListener { showFontSizeDialog() }
        binding.tvLyrics.textSize = FONT_SIZES_SP[fontSizeIndex()]
        binding.btnPlayerMenu.setOnClickListener { showPlayerMenu() }
        applyArtworkShape()
        showPage(page)
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

    //Меню за тремя точками (одно и то же для страниц «Песня» и «Текст песни»)
    private fun showPlayerMenu() {
        if (page == PAGE_QUEUE) { showQueueMenu(); return }
        val context = requireContext()
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_player_menu, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 8f
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        //галочка справа у текущей формы изображения
        val round = isRoundArtwork()
        val itemRound = popupView.findViewById<TextView>(R.id.itemShapeRound)
        val itemSquare = popupView.findViewById<TextView>(R.id.itemShapeSquare)
        val check = ContextCompat.getDrawable(context, R.drawable.ic_pl_check)
        (if (round) itemRound else itemSquare).setCompoundDrawablesRelativeWithIntrinsicBounds(
            ContextCompat.getDrawable(context, if (round) R.drawable.ic_shape_circle else R.drawable.ic_shape_square),
            null, check, null
        )

        popupView.findViewById<TextView>(R.id.itemPlayerChangeCover).setOnClickListener {
            popup.dismiss()
            viewModel.getCurrentSong()?.let { song ->
                viewModel.setSelectedSong(song)
                findNavController().navigate(R.id.coverChangeFragment)
            }
        }
        popupView.findViewById<TextView>(R.id.itemPlayerShare).setOnClickListener {
            popup.dismiss()
            viewModel.getCurrentSong()?.let { shareSong(context, it) }
        }
        itemRound.setOnClickListener { setArtworkRound(true); popup.dismiss() }
        itemSquare.setOnClickListener { setArtworkRound(false); popup.dismiss() }

        popup.showAsDropDown(binding.btnPlayerMenu, 0, 0, Gravity.END)
    }

    //Меню очереди (для плейлиста): те же действия, что в меню строки списка плейлистов
    private fun showQueueMenu() {
        val context = requireContext()
        val source = appPrefs.getPlaybackSource()
        val playlist = playlistsViewModel.playlists.value?.firstOrNull { it.id == source?.id }
        if (source?.type != SourceType.PLAYLIST || playlist == null) return
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_queue_menu, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 8f
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val toSource = {
            findNavController().navigate(R.id.addSongsSourceFragment, bundleOf("playlistId" to playlist.id))
        }
        popupView.findViewById<TextView>(R.id.itemQueueAdd).setOnClickListener {
            popup.dismiss()
            playlistsViewModel.addByTimeLimitMs = null
            toSource()
        }
        popupView.findViewById<TextView>(R.id.itemQueueAddByTime).setOnClickListener {
            popup.dismiss()
            showTimeLimitDialog(context, playlist) { totalMs ->
                playlistsViewModel.addByTimeLimitMs = totalMs
                toSource()
            }
        }
        //удалять нечего, если в плейлисте нет треков
        val hasSongs = playlist.playlistSongs.isNotEmpty()
        popupView.findViewById<TextView>(R.id.itemQueueRemove).visibility = if (hasSongs) View.VISIBLE else View.GONE
        popupView.findViewById<View>(R.id.dividerQueueRemove).visibility = if (hasSongs) View.VISIBLE else View.GONE
        popupView.findViewById<TextView>(R.id.itemQueueRemove).setOnClickListener {
            popup.dismiss()
            findNavController().navigate(
                R.id.removeSongsFromPlaylistFragment,
                bundleOf("playlistId" to playlist.id, "playlistName" to playlist.playlistName)
            )
        }
        popup.showAsDropDown(binding.btnPlayerMenu, 0, 0, Gravity.END)
    }

    private fun isRoundArtwork(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(PREF_ARTWORK_ROUND, false)

    private fun setArtworkRound(round: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit().putBoolean(PREF_ARTWORK_ROUND, round).apply()
        applyArtworkShape()
    }

    //Форма обложки: квадратная (как была) или круглая. В развёрнутом виде (на всю страницу) форма не применяется.
    //Круглая: обложка обрезается кругом и заполняет его (centerCrop); зум по щипку остаётся.
    private fun applyArtworkShape() {
        val art = binding.artworkImageView
        val round = isRoundArtwork() && !isExpanded
        art.round = round
        art.scaleType = if (round) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        //круг чуть крупнее квадрата, чтобы выглядел не мельче
        if (!isExpanded) {
            val params = art.layoutParams as ConstraintLayout.LayoutParams
            params.matchConstraintPercentWidth = if (isRoundArtwork()) 0.66f else 0.6f
            art.layoutParams = params
        }
    }

    //Очередь воспроизведения: список песен плеера, строки перетаскиваются
    private fun setupQueue() {
        queueAdapter = QueueAdapter(
            onClick = { index -> playerViewModel.playQueueTrack(index) },
            onMoved = { from, to -> playerViewModel.moveQueueTrack(from, to) },
            onDropped = { songs -> saveQueueOrder(songs) }
        )
        queueTouchHelper = ItemTouchHelper(ItemTouchHelperCallback(queueAdapter))
        queueAdapter.touchHelper = queueTouchHelper
        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueue.adapter = queueAdapter
        queueTouchHelper.attachToRecyclerView(binding.rvQueue)
    }

    private fun refreshQueue() {
        queueAdapter.submit(playerViewModel.queue(), playerViewModel.currentQueueIndex())
        refreshQueueLabel()
    }

    //подпись вкладки «очередь»: номер играющей песни из общего числа
    private fun refreshQueueLabel() {
        val queue = playerViewModel.queue()
        binding.tvQueueLabel.text = "#${playerViewModel.currentQueueIndex() + 1}/${queue.size}"
    }

    //Новый порядок очереди: если играет плейлист, запоминаем порядок и в самом плейлисте
    private fun saveQueueOrder(songs: List<Song>) {
        val source = appPrefs.getPlaybackSource()
        if (source?.type == SourceType.PLAYLIST) {
            playlistsViewModel.setCurrentPlaylistByPlaylistId(source.id) {
                playlistsViewModel.updatePlaylistSongsOrder(songs) { }
            }
        }
    }

    //Плейлист могли изменить на других экранах (добавили, удалили треки): очередь плеера приводим к нему
    private fun syncQueueWithPlaylist() {
        val source = appPrefs.getPlaybackSource() ?: return
        if (source.type != SourceType.PLAYLIST) return
        playlistsViewModel.setCurrentPlaylistByPlaylistId(source.id) { playlist ->
            if (_binding == null || playlist == null) return@setCurrentPlaylistByPlaylistId
            if (playlist.playlistSongs.isNotEmpty()) playerViewModel.syncQueue(playlist.playlistSongs)
            if (page == PAGE_QUEUE) refreshQueue() else refreshQueueLabel()
        }
    }

    private fun isPlaylistSource() = appPrefs.getPlaybackSource()?.type == SourceType.PLAYLIST

    //Переключение страниц «Очередь» / «Песня» / «Текст песни» и подсветка выбранной вкладки
    private fun showPage(newPage: Int) {
        page = newPage
        backToSongCallback.isEnabled = page != PAGE_SONG
        binding.rvQueue.visibility = if (page == PAGE_QUEUE) View.VISIBLE else View.GONE
        binding.pageSong.visibility = if (page == PAGE_SONG) View.VISIBLE else View.GONE
        binding.pageLyrics.visibility = if (page == PAGE_LYRICS) View.VISIBLE else View.GONE
        styleTab(binding.tvQueueLabel, binding.tabQueueIcon, page == PAGE_QUEUE)
        styleTab(binding.tvSongLabel, binding.tabSongIcon, page == PAGE_SONG)
        styleTab(binding.tvLyricsLabel, binding.tabLyricsIcon, page == PAGE_LYRICS)
        if (page == PAGE_QUEUE) refreshQueue()
        //у очереди меню есть только для плейлиста
        binding.btnPlayerMenu.visibility =
            if (page == PAGE_QUEUE && !isPlaylistSource()) View.INVISIBLE else View.VISIBLE
    }

    private fun styleTab(label: TextView, icon: ImageView, active: Boolean) {
        val selected = ContextCompat.getColor(requireContext(), R.color.white)
        val normal = (selected and 0x00FFFFFF) or (0x99 shl 24)
        label.setTextColor(if (active) selected else normal)
        label.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        icon.alpha = if (active) 1f else 0.6f
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
            applyArtworkShape()
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
                params.matchConstraintPercentWidth = if (isRoundArtwork()) 0.66f else 0.6f
                binding.artworkImageView.scaleType =
                    if (isRoundArtwork()) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
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
            refreshQueueLabel()

            //находим индекс трека в плейлисте
            val indexOfTrack =
                songAndPlaylist.playlist.indexOfFirst {song->
                    song.mediaUri == currentSong?.mediaUri
                }
            //перед передачей в Handler меняем в передаваемом плейлисте artUri для текущего трека
            //песни может не быть в сохранённом списке (очередь изменили: добавили/убрали треки) - берём обложку у самой песни
            val trackArtUri = if (indexOfTrack >= 0) {
                songAndPlaylist.playlist[indexOfTrack].artUri = currentSong?.artUri
                songAndPlaylist.playlist[indexOfTrack].artUri
            } else {
                currentSong?.artUri
            }
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
                if (isPlaying) R.drawable.ic_pause_accent else R.drawable.ic_play_accent
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
        //играющая песня сменилась: подсветка в очереди и подпись вкладки
        queueAdapter.currentIndex = playerViewModel.currentQueueIndex()
        refreshQueueLabel()
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
        private const val STATE_PAGE = "state_page"
        private const val PAGE_QUEUE = 0
        private const val PAGE_SONG = 1
        private const val PAGE_LYRICS = 2
        private const val PREF_ARTWORK_ROUND = "player_artwork_round"
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
