package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.accentColor
import com.example.muzpleer.util.showStripByDefault
import com.example.muzpleer.util.SongSortField
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.util.scrollToPlayingRow
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentPlaylistBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.adapters.PlaylistAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class PlaylistFragment():Fragment() {
    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private lateinit var adapter: PlaylistAdapter
    private var isEditMode = false
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var stripVisible = showStripByDefault()   //по умолчанию как в настройках («Панель с настройками»), после возврата на экран тоже
    private var eyeItem: MenuItem? = null
    // Флаг, чтобы различать состояния "список пуст изначально" и "ничего не найдено"
    private var isSearchActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //viewModel.setAppBarTitle("Плейлисты") //сбивает правильные заголовки для TabFragment

        // Изначально показываем картинку-заглушку и крутилку
        showLoadingState()

        adapter = PlaylistAdapter (viewModel, playlistsViewModel){ playlist ->
            //здесь setPlaylist и setSongAndPlaylist не делаем, так как здесь нет песен

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("playlistId", playlist.id)
                putString("playlistName", playlist.playlistName)
                Log.d(TAG,"33 PlaylistFragment onViewCreated bundle: playlist = ${playlist.id} " +
                        " playlistName = ${playlist.playlistName} from = 5 ")
            }
            findNavController().navigate( R.id.songPlaylistFragment, bundle)
        }

        // Настраиваем ItemTouchHelper
        val callback = ItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)

        // Изначально НЕ прикрепляем - прикрепим только в режиме редактирования в toggleEditMode
        // itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)

        binding.playlistRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlaylistFragment.adapter
        }

        binding.fabNewPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        //подсветка плейлистов, в которых лежит играющий трек: пересчитываем при смене трека и при изменении плейлистов
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            playlistsViewModel.refreshPlaylistsWithSong(song?.id)
        }
        playlistsViewModel.playlists.observe(viewLifecycleOwner) {
            playlistsViewModel.refreshPlaylistsWithSong(viewModel.currentSong.value?.id)
        }

        playlistsViewModel.filteredPlaylists.observe(viewLifecycleOwner) { filteredPlaylists ->
            Log.d(TAG,"53 PlaylistFragment onViewCreated filteredPlaylists.observe: filteredPlaylists.size= ${filteredPlaylists.size} ")
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок
            adapter.playlist = playlistsViewModel.sortForDisplay(filteredPlaylists)  //передаём данные в адаптер (порядок как выбран в панели сортировки)
            //значок «К треку» нужен, только если список не помещается на экране
            binding.stripGoTo.visibility = if (filteredPlaylists.size > 7) View.VISIBLE else View.GONE

            // Управляем видимостью в зависимости от состояния
            updateEmptyState(filteredPlaylists)
        }

        setupStrip()
    }

    //значки полоски под тулбаром: переход к играющему треку, порядок строк, сортировка
    private fun setupStrip() {
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripGoTo, "К играющему треку")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripSort, getString(R.string.strip_sort))
        binding.stripGoTo.setOnClickListener {
            //к строке, где играющий трек (она подсвечена эквалайзером)
            binding.playlistRecyclerView.scrollToPlayingRow(
                adapter.playlist.indexOfFirst { it.id in (playlistsViewModel.playlistIdsWithSong.value ?: emptySet()) }
            )
        }
        binding.stripEdit.setOnClickListener {
            //перетаскивание меняет свой порядок: при сортировке переключаемся на «Без сортировки»
            if (!isEditMode && playlistsViewModel.sortField != SongSortField.NONE) {
                playlistsViewModel.setSort(SongSortField.NONE, playlistsViewModel.sortAscending)
                binding.sortPanel.refresh()
                updateStripIcons()
            }
            toggleEditMode()
        }
        binding.stripSort.setOnClickListener { binding.sortPanel.toggle() }
        binding.sortPanel.setAvailable(setOf(SongSortField.NONE, SongSortField.TITLE, SongSortField.COUNT, SongSortField.DURATION))
        binding.sortPanel.bind({ playlistsViewModel.sortField }, { playlistsViewModel.sortAscending }) { field, asc ->
            playlistsViewModel.setSort(field, asc)
            if (field != SongSortField.NONE && isEditMode) toggleEditMode()
            updateStripIcons()
        }
        applyStripState()
        updateStripIcons()
    }

    private fun applyStripState() {
        _binding?.listStrip?.visibility = if (stripVisible) View.VISIBLE else View.GONE
        eyeItem?.setIcon(if (stripVisible) R.drawable.ic_visibility_24 else R.drawable.ic_visibility_off_24)
    }

    //режим правки порядка и включённая сортировка окрашены цветом акцента
    private fun updateStripIcons() {
        if (_binding == null) return
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val accent = requireContext().accentColor()
        binding.stripEdit.imageTintList = android.content.res.ColorStateList.valueOf(if (isEditMode) accent else white)
        binding.stripSort.imageTintList = android.content.res.ColorStateList.valueOf(
            if (playlistsViewModel.sortField != SongSortField.NONE) accent else white)
        androidx.appcompat.widget.TooltipCompat.setTooltipText(
            binding.stripEdit, if (isEditMode) "Редактирование порядка" else "Редактировать порядок")
    }

    /**
     * Показать состояние загрузки: картинка + прогрессбар, текст скрыт.
     * Вызывается при открытии фрагмента.
     */
    private fun showLoadingState() {
        binding.emptyImageViewPlaylists.visibility = View.VISIBLE
        binding.progressBarPlaylists.visibility = View.VISIBLE
        binding.tvEmptyPlaylists.visibility = View.GONE
    }

    /**
     * Обновить состояние пустого списка.
     * @param playlists текущий список (после фильтрации).
     */
    private fun updateEmptyState(playlists: List<Playlist>) {
        if (playlists.isEmpty()) {
            // Список пуст — скрываем прогрессбар, показываем картинку и текст
            binding.progressBarPlaylists.visibility = View.GONE
            binding.emptyImageViewPlaylists.visibility = View.VISIBLE
            binding.tvEmptyPlaylists.visibility = View.VISIBLE

            // Выбираем текст в зависимости от того, активен ли поиск
            binding.tvEmptyPlaylists.text = if (isSearchActive) {
                "По вашему запросу ничего не найдено"
            } else {
                "У вас пока нет плейлистов"
            }
        } else {
            // Список не пуст — всё скрываем
            binding.progressBarPlaylists.visibility = View.GONE
            binding.emptyImageViewPlaylists.visibility = View.GONE
            binding.tvEmptyPlaylists.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        //после настроек и других экранов панель снова по настройке
        stripVisible = showStripByDefault()
        applyStripState()
        // Сбрасываем режим редактирования при возвращении
        resetEditModeOnStart()
        // Обновляем меню
        requireActivity().invalidateOptionsMenu()
        updateStripIcons()
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()

        // Сбрасываем режим редактирования при уходе
        resetEditModeOnPause()
        //полоска со значками при уходе с экрана снова видна, панель сортировки убрана
        stripVisible = showStripByDefault()
        applyStripState()
        binding.sortPanel.hide(false)

        //определяем первую видимую позицию
        val manager = binding.playlistRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        Log.d(TAG, "PlaylistFragment onPause firstPosition = $firstPosition")
        playlistsViewModel.savePositionPlaylist(firstPosition)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): PlaylistFragment {
            this.viewPager = viewPager
            return PlaylistFragment()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_strip_search, menu)
        eyeItem = menu.findItem(R.id.action_toggle_strip)
        applyStripState()

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other)
        val searchView =searchItem.actionView as SearchView
        //значок лупы слева в развёрнутом сост и сворачиваем строку поиска (true)
        searchView.setIconifiedByDefault(true)
        //пишем подсказку в строке поиска
        searchView.queryHint = getString(R.string.search_playlist)
        //устанавливаем в панели действий кнопку ( > )для отправки поискового запроса
        searchView.isSubmitButtonEnabled = true
        //устанавливаем слушатель
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                // Устанавливаем флаг, что поиск активен, если есть текст
                isSearchActive = !newText.isNullOrEmpty()
                playlistsViewModel.filterPlaylists(newText.orEmpty())
                return true
            }
        })

        // Сбрасываем флаг поиска при закрытии SearchView
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                //клавиатура закрыла бы выдвинутую панель сортировки и список: панель убираем
                if (_binding != null) binding.sortPanel.hide()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchActive = false
                playlistsViewModel.filterPlaylists("") // сброс фильтра
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_toggle_strip -> {
                stripVisible = !stripVisible
                applyStripState()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun resetEditModeOnStart() {
        // Гарантируем, что при открытии фрагмента режим редактирования выключен
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
        }
    }

    private fun resetEditModeOnPause() {
        // Сбрасываем режим редактирования при уходе с фрагмента
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
            Log.d(TAG, "PlaylistFragment: Режим редактирования сброшен при уходе")
        }
    }

    private fun toggleEditMode() {

        isEditMode = !isEditMode
        adapter.setEditMode(isEditMode)

        // Включаем/выключаем возможность перетаскивания
        if (isEditMode) {
            itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
        }

        updateStripIcons()
    }

    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте треки для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

    private fun showCreatePlaylistDialog() {
        //после создания список обновится сам через observer filteredPlaylists
        com.example.muzpleer.util.showCreatePlaylistDialog(requireContext()) { name ->
            playlistsViewModel.createPlaylist(name)
        }
    }

}