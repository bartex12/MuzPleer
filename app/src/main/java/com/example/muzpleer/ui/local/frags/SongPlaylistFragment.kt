package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.indexOfSong
import com.example.muzpleer.util.accentColor
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentAlltracksForPlaylistBinding
import com.example.muzpleer.model.PlaybackSource
import com.example.muzpleer.model.SourceType
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.SongsPlaylistAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.player.PlayerViewModel
import com.example.muzpleer.util.SongSortField
import com.example.muzpleer.util.showStripByDefault
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SongPlaylistFragment:Fragment() {
    private var _binding: FragmentAlltracksForPlaylistBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongsPlaylistAdapter
    private val viewModel: SharedViewModel by activityViewModel()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false
    private  var playlistId:Long = -1
    private var totalSongsCount = 0  // Храним общее количество песен в плейлисте(не отфильтрованных)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(arguments?.getLong("playlistId") != null) {
            playlistId = requireArguments().getLong("playlistId")
            Log.d(TAG, "!@#@ SongPlaylistFragment onCreate playlistId  $playlistId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlltracksForPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сброс режима редактирования при каждом открытии фрагмента
        resetEditModeOnStart()

        // Получаем название плейлиста из аргументов или ViewModel
        val playlistName = arguments?.getString("playlistName")
            ?: playlistsViewModel.getCurrentPlaylist()?.playlistName

        // Устанавливаем заголовок
        updateToolbarTitle("Плейлист: $playlistName")

        adapter = SongsPlaylistAdapter(viewModel, playlistsViewModel, favoritesViewModel) { song ->
            //очередь воспроизведения совпадает с порядком на экране (при «Без сортировки» - порядок плейлиста)
            val playlistSongs = playlistsViewModel.sortSongsForDisplay(
                playlistsViewModel.getCurrentPlaylist()?.playlistSongs ?: listOf())
            //можно было взять и из currentFilteredPlaylistSongs
            Log.d(
                TAG, "!@#@ SongPlaylistFragment размер плейлиста = " +
                        "${playlistSongs.size} имя первой песни плейлиста " +
                        "= ${playlistSongs.first().title} "
            )

            viewModel.setPlaybackSource(
                PlaybackSource(SourceType.PLAYLIST, id = playlistId, name = arguments?.getString("playlistName").orEmpty())
            )
            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,
                    playlist=playlistSongs
                )
            )
            viewModel.setCurrentSong(song)
        }

        // Настраиваем ItemTouchHelper
        val callback=ItemTouchHelperCallback(adapter)
        itemTouchHelper=ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)
        itemTouchHelper.attachToRecyclerView(binding.alltracksPlaylistRecyclerView)

        binding.alltracksPlaylistRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SongPlaylistFragment.adapter
        }

        playlistsViewModel.resetSongSortIfOtherPlaylist(playlistId)

        playlistsViewModel.loadCurrentPlaylistForSongs(playlistId)  //грузим текущий плейлист ради песен для адаптера

        // Слушаем ОБЩИЙ список песен плейлиста (не отфильтрованный)
        playlistsViewModel.currentPlaylistSongs.observe(viewLifecycleOwner) { playlistSongs ->
            totalSongsCount =playlistSongs?.size ?:0
            updateMenuVisibility()  //строка поиска в тулбаре есть, только если в плейлисте есть треки

            Log.d(TAG, "###3 SongPlaylistFragment total songs: $totalSongsCount")
        }

        playlistsViewModel.currentFilteredPlaylistSongs.observe(viewLifecycleOwner) { currentPlaylistSongs ->
            Log.d(TAG, "!@#@4 SongPlaylistFragment currentFilteredPlaylistSongs.observe currentFilteredPlaylistSongs size ${currentPlaylistSongs?.size}")
            val currentSongs = currentPlaylistSongs?: listOf()
            //adapter.data = getSortedDataSong(currentSongs) //нельзя, собьётся перемещение строк
            adapter.data = playlistsViewModel.sortSongsForDisplay(currentSongs)

            // Обновляем видимость пустого состояния (только для фильтрации)
            val showEmptyState =currentPlaylistSongs?.isEmpty() != false//пусто после поиска
            val  songCountLessZero = totalSongsCount <= 0  // нет песен в плейлисте

            //tvEmptyPlaylistSong-текст  emptyImageViewPlaylist-картинка
            binding.tvEmptyPlaylistSong.visibility =
                if (showEmptyState || songCountLessZero) View.VISIBLE else View.GONE
            binding.emptyImageViewPlaylist.visibility =
                if (songCountLessZero) View.VISIBLE else View.GONE

            // Обновляем текст пустого состояния
            if (showEmptyState && !songCountLessZero) {
                binding.tvEmptyPlaylistSong.text = "По вашему запросу ничего не найдено"
            } else if (songCountLessZero) {
                binding.tvEmptyPlaylistSong.text = "Здесь пока нет ни одного трека"
            }

            //пункты меню обновляем на месте: пересоздание меню во время ввода закрывало бы строку поиска
            updateMenuVisibility()
        }

        // Также подписываемся на изменения текущего плейлиста
        playlistsViewModel.currentPlaylist.observe(viewLifecycleOwner) { playlistWithSongs ->
            playlistWithSongs?.let {
                adapter.data = playlistsViewModel.sortSongsForDisplay(it.playlistSongs)
            }
        }

        setupStrip()
        initMenu()
    }

    private fun resetEditModeOnStart() {
        // Гарантируем, что при открытии фрагмента режим редактирования выключен
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
        }
    }

    override fun onResume() {
        super.onResume()
        //после других экранов панель снова по настройке
        stripVisible = showStripByDefault()
        applyStripState()
        // Также сбрасываем при возобновлении фрагмента
        resetEditModeOnStart()
        updateStripIcons()
    }

    override fun onPause() {
        super.onPause()
        //при уходе с экрана поиск закрывается, панель снова по настройке
        stripVisible = showStripByDefault()
        if (_binding != null) { applyStripState(); updateStripIcons(); hideSortPanel(false); hideKeyboard() }
        searchMenuItem?.collapseActionView()
        currentSearchQuery = ""
        playlistsViewModel.filterPlaylistSongs("")
        // Сбрасываем режим редактирования при уходе с фрагмента
        resetEditModeOnPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Сбрасываем заголовок при уходе с фрагмента
        (requireActivity() as? MainActivity)?.resetToTabTitle()
        // Сбрасываем режим редактирования при уничтожении вью
        resetEditModeOnPause()
        listMenu = null
        searchMenuItem = null
        _binding = null
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    private fun resetEditModeOnPause() {
        // Если был включен режим редактирования, выключаем его
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
            Log.d(TAG, "Режим редактирования сброшен при уходе с фрагмента")
        }
    }

    companion object {
        const val TAG = "33333"
        private const val ARG_ALLTRACKSLIST = "ARG_ALLTRACKSLIST"

        fun newInstance( alltrackslist: List<Song>): SongPlaylistFragment {
            return SongPlaylistFragment().apply {
                arguments = bundleOf(
                    ARG_ALLTRACKSLIST to ArrayList(alltrackslist))
            }
        }
    }

    private var stripVisible = showStripByDefault()   //по умолчанию как в настройках («Панель с настройками»)
    private var eyeItem: MenuItem? = null
    private var listMenu: Menu? = null
    private var searchMenuItem: MenuItem? = null
    private var currentSearchQuery = ""

    private fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_playlist_songs, menu)
                listMenu = menu
                setupSearchItem(menu)
                eyeItem = menu.findItem(R.id.action_toggle_strip)
                updateEyeIcon()
                updateMenuVisibility()
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.search_toolbar_playlist_songs)?.isVisible = totalSongsCount > 0
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_toggle_strip) {
                    stripVisible = !stripVisible
                    applyStripState()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    //строка поиска в тулбаре, как в списке «Все треки»
    private fun setupSearchItem(menu: Menu) {
        val searchItem = menu.findItem(R.id.search_toolbar_playlist_songs) ?: return
        searchMenuItem = searchItem
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
        searchView.setIconifiedByDefault(true)
        searchView.queryHint = getString(R.string.search_song)
        if (currentSearchQuery.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(currentSearchQuery, false)
        }
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText.orEmpty()
                playlistsViewModel.filterPlaylistSongs(currentSearchQuery)
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                //клавиатура закрыла бы выдвинутую панель сортировки и список: панель убираем
                if (_binding != null) hideSortPanel()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                currentSearchQuery = ""
                playlistsViewModel.filterPlaylistSongs("")
                return true
            }
        })
    }

    //пункты меню обновляем на месте: пересоздание меню во время ввода закрыло бы строку поиска
    private fun updateMenuVisibility() {
        if (_binding == null) return
        listMenu?.findItem(R.id.search_toolbar_playlist_songs)?.isVisible = totalSongsCount > 0
        val songsCount = playlistsViewModel.currentFilteredPlaylistSongs.value?.size ?: 0
        binding.stripGoTo.visibility = if (songsCount > 7) View.VISIBLE else View.GONE
    }

    //значки полоски: переход к треку, случайный порядок, порядок строк, сортировка
    private fun setupStrip() {
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripGoTo, "К треку")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripShuffle, "Случайный порядок")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripSort, getString(R.string.strip_sort))
        binding.stripGoTo.setOnClickListener {
            //ищем в том списке, что на экране (с учётом сортировки и поиска)
            val indexOfSong = adapter.data.indexOfSong(viewModel.getCurrentSong())
            (binding.alltracksPlaylistRecyclerView.layoutManager as LinearLayoutManager).let {
                if (indexOfSong >= 0) it.scrollToPositionWithOffset(indexOfSong, 0)
                else { it.scrollToPosition(0); Toast.makeText(requireContext(), "Играющий трек в этом списке не найден", Toast.LENGTH_SHORT).show() }
            }
        }
        binding.stripShuffle.setOnClickListener {
            val newShuffleState = playerViewModel.setPlaybackMode()
            updateStripIcons()
            Toast.makeText(context, if (newShuffleState) "Случайный порядок включен" else "Прямой порядок включен", Toast.LENGTH_SHORT).show()
        }
        binding.stripEdit.setOnClickListener { toggleEditMode() }
        binding.stripSort.setOnClickListener { binding.sortPanel.toggle() }
        setupSortPanel()
        applyStripState()
        updateStripIcons()
    }

    //панель «Сортировать по»: «Без сортировки» - порядок плейлиста, заданный перетаскиванием (по умолчанию)
    private fun setupSortPanel() {
        binding.sortPanel.setAvailable(setOf(SongSortField.NONE, SongSortField.TITLE,
            SongSortField.ARTIST, SongSortField.DURATION))
        binding.sortPanel.bind({ playlistsViewModel.songSortField }, { playlistsViewModel.songSortAscending }) { field, asc ->
            playlistsViewModel.setSongSort(playlistId, field, asc)
            if (field != SongSortField.NONE && isEditMode) toggleEditMode()
            updateSortUi()
        }
        updateSortUi()
    }

    //значок сортировки окрашен цветом акцента, пока сортировка включена
    private fun updateSortUi() {
        if (_binding == null) return
        val active = playlistsViewModel.songSortField != SongSortField.NONE
        binding.stripSort.imageTintList = android.content.res.ColorStateList.valueOf(
            if (active) requireContext().accentColor() else ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun hideSortPanel(animate: Boolean = true) = binding.sortPanel.hide(animate)

    private fun applyStripState() {
        binding.playlistSongsStrip.visibility = if (stripVisible) View.VISIBLE else View.GONE
        updateEyeIcon()
    }

    private fun updateEyeIcon() {
        eyeItem?.setIcon(if (stripVisible) R.drawable.ic_visibility_24 else R.drawable.ic_visibility_off_24)
    }

    //случайный порядок и режим правки порядка окрашены цветом акцента, когда включены
    private fun updateStripIcons() {
        if (_binding == null) return
        val ctx = requireContext()
        val white = ContextCompat.getColor(ctx, R.color.white)
        val accent = ctx.accentColor()
        val shuffle = playerViewModel.isShuffleMode()
        binding.stripShuffle.setImageResource(if (shuffle) R.drawable.baseline_shuffle_24 else R.drawable.icons8_data_white_24)
        binding.stripShuffle.imageTintList = android.content.res.ColorStateList.valueOf(if (shuffle) accent else white)
        binding.stripEdit.imageTintList = android.content.res.ColorStateList.valueOf(if (isEditMode) accent else white)
        androidx.appcompat.widget.TooltipCompat.setTooltipText(
            binding.stripEdit, if (isEditMode) "Редактирование порядка" else "Редактировать порядок")
    }

    private fun toggleEditMode() {
        //перетаскивание меняет свой порядок: при сортировке переключаемся на «Без сортировки»
        if (!isEditMode && playlistsViewModel.songSortField != SongSortField.NONE) {
            playlistsViewModel.setSongSort(playlistId, SongSortField.NONE, playlistsViewModel.songSortAscending)
            binding.sortPanel.refresh()
            updateSortUi()
        }
        val newEditMode = !isEditMode
        isEditMode = newEditMode
        adapter.setEditMode(newEditMode)

        // Включаем/выключаем возможность перетаскивания
        if (isEditMode) {
            itemTouchHelper.attachToRecyclerView(binding.alltracksPlaylistRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
        }
        updateStripIcons()
        Log.d(TAG, "Режим редактирования: $newEditMode")
    }

    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте треки для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

}
