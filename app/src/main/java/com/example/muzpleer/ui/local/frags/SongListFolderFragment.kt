package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.indexOfSong
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
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.util.scrollToPlayingRow
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import androidx.core.content.ContextCompat
import com.example.muzpleer.util.accentColor
import com.example.muzpleer.util.showStripByDefault
import com.example.muzpleer.util.SongSortField
import com.example.muzpleer.databinding.FragmentAlltracksBinding
import com.example.muzpleer.model.PlaybackSource
import com.example.muzpleer.model.SourceType
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.SongsAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.player.PlayerViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

//Фрагмент SongListFolderFragment – макет  fragment_alltracks - адаптер SongsAdapter -- Список
//песен конкретной  папки

class SongListFolderFragment:Fragment() {
    private var _binding: FragmentAlltracksBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongsAdapter
    private val viewModel: SharedViewModel by activityViewModel()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private var totalSongsCount = 0  // Храним общее количество песен (не отфильтрованных)
    private var folderName: String? = null //имя папки
    private var scrolledToImported = false
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderName = arguments?.getString("folderName")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlltracksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Устанавливаем заголовок
        updateToolbarTitle("Папка: $folderName")
        navController = findNavController()

        initMenu()

        adapter = SongsAdapter(viewModel, playlistsViewModel, favoritesViewModel, navController) { song ->
            //очередь собираем из списка, который показан на экране (иначе порядок воспроизведения не совпадёт с видимым);
            //при поиске на экране не весь список, поэтому тогда берём полный список в том же порядке
            val shown = this.adapter.data
            val playlist = if (shown.size == totalSongsCount) shown else viewModel.sortListSongs(foldersViewModel.listFolderSong.value ?: viewModel.getPlaylist())

            viewModel.setPlaybackSource(PlaybackSource(SourceType.FOLDER, key = arguments?.getString("folderPath").orEmpty(), name = folderName.orEmpty()))
            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,
                    playlist=playlist
                )
            )
            viewModel.setCurrentSong(song)
        }

        binding.alltracksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SongListFolderFragment.adapter
        }

        adapter.showAlbumInsteadOfArtist = false
        viewModel.resetListSortIfOther(sortKey)
        setupStrip()

        if(arguments?.getString("folderPath") != null) {
            val folderPath = arguments?.getString("folderPath")!!
            Log.d(TAG, "46!@# SongListFolderFragment arguments folderPath  $folderPath")
            foldersViewModel.getSongsByFolder(folderPath)

            // Слушаем ОБЩИЙ список избранных песен (не отфильтрованный)
            foldersViewModel.listFolderSong.observe(viewLifecycleOwner) { folderSongs ->
                totalSongsCount = folderSongs.size
                updateMenuVisibility()  //строка поиска в тулбаре есть, только если в списке есть треки
                Log.d(TAG, "###SongListFolderFragment totalSongsCount: $totalSongsCount")
            }

            foldersViewModel.filteredListFolderSong.observe(viewLifecycleOwner) { folderSongs ->
                Log.d(TAG, "47!@# SongListFolderFragment filteredListFolderSong.observe " +
                        "folderSongs size ${folderSongs.size} ")
                //здесь нельзя делать сортировку, иначе собьётся перемещение !!!
                //adapter.data = getSortedDataSong(folderSongs)
                     shownSongs = folderSongs
                adapter.data = viewModel.sortListSongs(folderSongs)
                //пришли из приёма трека: прокручиваем список к добавленному треку (один раз)
                val scrollId = arguments?.getLong("scrollToSongId", -1L) ?: -1L
                if (scrollId != -1L && !scrolledToImported) {
                    val index = adapter.data.indexOfFirst { it.id == scrollId }
                    if (index >= 0) {
                        scrolledToImported = true
                        binding.alltracksRecyclerView.post {
                            _binding?.alltracksRecyclerView?.scrollToPlayingRow(index)
                        }
                    }
                }

                // Обновляем видимость пустого состояния (только для фильтрации)
                val showEmptyState = folderSongs.isEmpty() //пусто после поиска
                val  songCountLessZero = totalSongsCount <= 0  // нет песен в избранном

                //favoriteEmpty-текст  emptyImageViewFavorite-картинка
                binding.favoriteEmpty.visibility =
                    if (showEmptyState || songCountLessZero) View.VISIBLE else View.GONE
                binding.emptyImageViewFavoriteAllTrack.visibility =
                    if (songCountLessZero) View.VISIBLE else View.GONE

                // Обновляем текст пустого состояния
                if (showEmptyState && !songCountLessZero) {
                    binding.favoriteEmpty.text = "По вашему запросу ничего не найдено"
                } else if (songCountLessZero) {
                    binding.favoriteEmpty.text = "Здесь пока нет ни одного трека"
                }

                // обновляем меню при загрузке данных
                updateMenuVisibility()
            }

            //обновление обложки при её замене
            viewModel.coverImageUri.observe(viewLifecycleOwner) { uri ->
                val selectedSong = viewModel.getSelectedSong()
                selectedSong?. let{selectedSong->
                    selectedSong.artUri = uri.toString()
                    Log.d(TAG,"48!@# SongListFolderFragment coverImageUri.observe uri = $uri ")
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //меняем заголовок тулбара по кнопке Назад
        (requireActivity() as? MainActivity)?.resetToTabTitle()
        listMenu = null
        searchMenuItem = null
        _binding = null
    }

    companion object {
        const val TAG = "33333"
        private const val ARG_ALLTRACKSLIST = "ARG_ALLTRACKSLIST"

        fun newInstance( alltrackslist: List<Song>): SongListFolderFragment {
            return SongListFolderFragment().apply {
                arguments = bundleOf(
                    ARG_ALLTRACKSLIST to ArrayList(alltrackslist))
            }
        }
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    private var stripVisible = showStripByDefault()   //по умолчанию как в настройках («Панель с настройками»)
    private var eyeItem: MenuItem? = null
    private var listMenu: Menu? = null
    private var searchMenuItem: MenuItem? = null
    private var currentSearchQuery = ""
    private val sortKey get() = "folder:${arguments?.getString("folderPath")}"
    private var shownSongs: List<Song> = listOf()  //список после поиска, до сортировки

    //пункты сортировки у каждого списка свои
    private val sortFields = setOf(SongSortField.NONE, SongSortField.TITLE, SongSortField.PATH, SongSortField.DURATION)

    override fun onResume() {
        super.onResume()
        //после других экранов панель снова по настройке
        stripVisible = showStripByDefault()
        applyStripState()
        updateStripIcons()
    }

    override fun onPause() {
        super.onPause()
        //при уходе с экрана поиск закрывается, панель снова по настройке
        stripVisible = showStripByDefault()
        if (_binding != null) { applyStripState(); updateStripIcons(); hideSortPanel(false); hideKeyboard() }
        searchMenuItem?.collapseActionView()
        currentSearchQuery = ""
        foldersViewModel.filterFolderSongs("")
    }

    private fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_strip_search, menu)
                listMenu = menu
                setupSearchItem(menu)
                eyeItem = menu.findItem(R.id.action_toggle_strip)
                updateEyeIcon()
                updateMenuVisibility()
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.search_toolbar_other)?.isVisible = totalSongsCount > 0
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
        val searchItem = menu.findItem(R.id.search_toolbar_other) ?: return
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
                foldersViewModel.filterFolderSongs(currentSearchQuery)
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
                foldersViewModel.filterFolderSongs("")
                return true
            }
        })
    }

    //пункты меню обновляем на месте: пересоздание меню во время ввода закрыло бы строку поиска
    private fun updateMenuVisibility() {
        if (_binding == null) return
        listMenu?.findItem(R.id.search_toolbar_other)?.isVisible = totalSongsCount > 0
        binding.stripGoTo.visibility = if (shownSongs.size > 7) View.VISIBLE else View.GONE
    }

    //значки полоски: переход к треку, случайный порядок, сортировка
    private fun setupStrip() {
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripGoTo, "К треку")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripShuffle, "Случайный порядок")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripSort, getString(R.string.strip_sort))
        binding.stripGoTo.setOnClickListener {
            //ищем в том списке, что на экране (с учётом сортировки и поиска)
            val indexOfSong = adapter.data.indexOfSong(viewModel.getCurrentSong())
            (binding.alltracksRecyclerView.layoutManager as LinearLayoutManager).let {
                if (indexOfSong >= 0) it.scrollToPositionWithOffset(indexOfSong, 0)
                else { it.scrollToPosition(0); Toast.makeText(requireContext(), "Играющий трек в этом списке не найден", Toast.LENGTH_SHORT).show() }
            }
        }
        binding.stripShuffle.setOnClickListener {
            val newShuffleState = playerViewModel.setPlaybackMode()
            updateStripIcons()
            Toast.makeText(context, if (newShuffleState) "Случайный порядок включен" else "Прямой порядок включен", Toast.LENGTH_SHORT).show()
        }
        binding.stripSort.setOnClickListener { binding.sortPanel.toggle() }
        setupSortPanel()
        applyStripState()
        updateStripIcons()
    }

    //панель «Сортировать по»: «Без сортировки» - порядок списка по умолчанию
    private fun setupSortPanel() {
        binding.sortPanel.setAvailable(sortFields)
        binding.sortPanel.bind({ viewModel.listSortField }, { viewModel.listSortAscending }) { field, asc ->
            viewModel.setListSort(sortKey, field, asc)
            adapter.data = viewModel.sortListSongs(shownSongs)
            updateSortUi()
        }
        updateSortUi()
    }

    //значок сортировки окрашен цветом акцента, пока сортировка включена
    private fun updateSortUi() {
        if (_binding == null) return
        val active = viewModel.listSortField != SongSortField.NONE
        binding.stripSort.imageTintList = android.content.res.ColorStateList.valueOf(
            if (active) requireContext().accentColor() else ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun hideSortPanel(animate: Boolean = true) = binding.sortPanel.hide(animate)

    private fun applyStripState() {
        binding.songListStrip.visibility = if (stripVisible) View.VISIBLE else View.GONE
        updateEyeIcon()
    }

    private fun updateEyeIcon() {
        eyeItem?.setIcon(if (stripVisible) R.drawable.ic_visibility_24 else R.drawable.ic_visibility_off_24)
    }

    //случайный порядок окрашен цветом акцента, когда включён
    private fun updateStripIcons() {
        if (_binding == null) return
        val ctx = requireContext()
        val shuffle = playerViewModel.isShuffleMode()
        binding.stripShuffle.setImageResource(if (shuffle) R.drawable.baseline_shuffle_24 else R.drawable.icons8_data_white_24)
        binding.stripShuffle.imageTintList = android.content.res.ColorStateList.valueOf(
            if (shuffle) ctx.accentColor() else ContextCompat.getColor(ctx, R.color.white))
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

}
