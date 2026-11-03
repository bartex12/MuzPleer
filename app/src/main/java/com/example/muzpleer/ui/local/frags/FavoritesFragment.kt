package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.indexOfSong
import com.example.muzpleer.util.accentColor
import com.example.muzpleer.util.showStripByDefault
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
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentFavoriteBinding
import com.example.muzpleer.model.PlaybackSource
import com.example.muzpleer.model.SourceType
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.FavoritesAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.SongSortField
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.player.PlayerViewModel
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class FavoritesFragment: Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private lateinit var adapter: FavoritesAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false
    private var searchMenuItem: MenuItem? = null //пункт поиска в тулбаре
    private var currentSearchQuery = ""
    private var totalSongsCount = 0  // Храним общее количество песен (не отфильтрованных)
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сброс режима редактирования при каждом открытии фрагмента
        resetEditModeOnStart()
        navController = findNavController()

        adapter = FavoritesAdapter(viewModel, playlistsViewModel, favoritesViewModel, navController) { song ->
            //устанавливаем список песен как плейлист
            val playlist=favoritesViewModel.getFavoriteSongs()
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист
            viewModel.setPlaybackSource(PlaybackSource(SourceType.FAVORITES))
            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,  //текущая песня
                    playlist=playlist //текущий плейлист
                )
            )
        }

        // Настраиваем ItemTouchHelper
        val callback=ItemTouchHelperCallback(adapter)
        itemTouchHelper=ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)
        itemTouchHelper.attachToRecyclerView(binding.favoriteRecyclerView)

        binding.favoriteRecyclerView.apply {
            layoutManager=LinearLayoutManager(requireContext())
            adapter=this@FavoritesFragment.adapter
        }

        //загружаем избранные песни
        favoritesViewModel.loadFavoriteSongs()

        // Слушаем ОБЩИЙ список избранных песен (не отфильтрованный)
        favoritesViewModel.favoriteSongs.observe(viewLifecycleOwner) { allFavorites ->
            totalSongsCount = allFavorites.size
            updateMenuVisibility()  //строка поиска в тулбаре есть, только если в избранном есть треки

            Log.d(TAG, "###FavoritesFragment total songs: $totalSongsCount")
        }

        favoritesViewModel.filteredFavoriteSongs.observe(viewLifecycleOwner) { filteredFavorites ->
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок!!!
            //val sortedData = getSortedDataSong(filteredFavorites)
            // adapter.data = sortedData  //передаём данные в адаптер
            adapter.data = filteredFavorites  //передаём данные в адаптер

            // Обновляем видимость пустого состояния (только для фильтрации)
            val showEmptyState = filteredFavorites.isEmpty() //пусто после поиска
            val  songCountLessZero = totalSongsCount <= 0  // нет песен в избранном

            //favoriteEmpty-текст  emptyImageViewFavorite-картинка
            binding.favoriteEmpty.visibility =
                if (showEmptyState || songCountLessZero) View.VISIBLE else View.GONE
            binding.emptyImageViewFavorite.visibility =
                if (songCountLessZero) View.VISIBLE else View.GONE

            // Обновляем текст пустого состояния
            if (showEmptyState && !songCountLessZero) {
                binding.favoriteEmpty.text = "По вашему запросу ничего не найдено"
            } else if (songCountLessZero) {
                binding.favoriteEmpty.text = "Здесь пока нет ни одного трека"
            }

            //пункты меню обновляем на месте: пересоздание меню во время ввода закрывало бы строку поиска и сбрасывало раскладку клавиатуры
            updateMenuVisibility()
        }

        //восстанавливаем позицию списка после поворота или возвращения на экран и при новой загрузке
        binding.favoriteRecyclerView.layoutManager?.scrollToPosition(favoritesViewModel.getPositionFavoriteSong())

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

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        //после настроек и других экранов панель снова по настройке
        stripVisible = showStripByDefault()
        applyStripState()
        // Также сбрасываем при возобновлении фрагмента
        resetEditModeOnStart()

        updateStripIcons()
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()

        //при уходе с экрана поиск закрывается: при возврате список не должен остаться урезанным
        //полоска со значками при уходе с экрана снова видна
        stripVisible = showStripByDefault()
        if (_binding != null) { applyStripState(); updateStripIcons(); hideSortPanel(false) }
        hideKeyboard()
        searchMenuItem?.collapseActionView()
        currentSearchQuery = ""
        favoritesViewModel.filterFavoriteSongs("")

        // Сбрасываем режим редактирования при уходе с фрагмента
        resetEditModeOnPause()

        //определяем первую видимую позицию
        val manager = binding.favoriteRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        favoritesViewModel.savePositionFavoriteSong(firstPosition)
        Log.d(TAG, "SongFragment onPause firstPosition = $firstPosition")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Сбрасываем режим редактирования при уничтожении вью
        resetEditModeOnPause()
        favMenu = null
        searchMenuItem = null
        _binding = null
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
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): FavoritesFragment {
            this.viewPager = viewPager
            return FavoritesFragment()
        }
    }

    private var stripVisible = showStripByDefault()   //по умолчанию как в настройках («Панель с настройками»), после возврата на экран тоже
    private var eyeItem: MenuItem? = null

    fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_favorites, menu)
                favMenu = menu
                setupSearchItem(menu)
                eyeItem = menu.findItem(R.id.action_toggle_strip)
                updateEyeIcon()
            }

            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                menu.findItem(R.id.search_toolbar_favorite)?.isVisible = totalSongsCount > 0
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

    //значки полоски под списком: переход к треку, случайный порядок, порядок строк, сортировка
    private fun setupStrip() {
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripGoTo, "К треку")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripShuffle, "Случайный порядок")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.stripSort, getString(R.string.strip_sort))
        binding.stripGoTo.setOnClickListener {
            val favoriteSongs = favoritesViewModel.getFavoriteSongs()
            val indexOfSong = favoriteSongs.indexOfSong(viewModel.getCurrentSong())
            (binding.favoriteRecyclerView.layoutManager as LinearLayoutManager).let {
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
        binding.stripSort.setOnClickListener { toggleSortPanel() }
        setupSortPanel()
        applyStripState()
        updateStripIcons()
    }

    //панель «Сортировать по»: «Без сортировки» - свой порядок, заданный перетаскиванием (по умолчанию)
    private fun setupSortPanel() {
        binding.sortPanel.bind({ favoritesViewModel.sortField }, { favoritesViewModel.sortAscending }) { field, asc ->
            favoritesViewModel.setSort(field, asc)
            if (field != SongSortField.NONE && isEditMode) toggleEditMode()
            updateSortUi()
        }
        updateSortUi()
    }

    //значок сортировки окрашен цветом акцента, пока сортировка включена
    private fun updateSortUi() {
        if (_binding == null) return
        val active = favoritesViewModel.sortField != SongSortField.NONE
        binding.stripSort.imageTintList = android.content.res.ColorStateList.valueOf(
            if (active) requireContext().accentColor() else ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun toggleSortPanel() = binding.sortPanel.toggle()

    private fun hideSortPanel(animate: Boolean = true) = binding.sortPanel.hide(animate)

    private fun applyStripState() {
        binding.favoriteStrip.visibility = if (stripVisible) View.VISIBLE else View.GONE
        updateEyeIcon()
    }

    private fun updateEyeIcon() {
        eyeItem?.setIcon(if (stripVisible) R.drawable.ic_visibility_24 else R.drawable.ic_visibility_off_24)
    }

    //цвета значков: случайный порядок и режим правки порядка окрашены цветом акцента, когда включены
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

    private var favMenu: Menu? = null

    private fun updateMenuVisibility() {
        if (_binding == null) return
        favMenu?.findItem(R.id.search_toolbar_favorite)?.isVisible = totalSongsCount > 0
        val songsCount = favoritesViewModel.filteredFavoriteSongs.value?.size ?: 0
        binding.stripGoTo.visibility = if (songsCount > 7) View.VISIBLE else View.GONE
    }

    //строка поиска в тулбаре, как в списке «Все треки»
    private fun setupSearchItem(menu: Menu) {
        val searchItem = menu.findItem(R.id.search_toolbar_favorite) ?: return
        searchMenuItem = searchItem
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
        searchView.setIconifiedByDefault(true)
        searchView.queryHint = getString(R.string.search_favorite)
        //сохраняем состояние поиска при пересоздании меню и повороте экрана
        if (currentSearchQuery.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(currentSearchQuery, false)
        }
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText.orEmpty()
                favoritesViewModel.filterFavoriteSongs(currentSearchQuery)
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
                favoritesViewModel.filterFavoriteSongs("")
                return true
            }
        })
    }

    private fun toggleEditMode() {
        //перетаскивание меняет свой порядок: при сортировке переключаемся на «Без сортировки»
        if (!isEditMode && favoritesViewModel.sortField != SongSortField.NONE) {
            favoritesViewModel.setSort(SongSortField.NONE, favoritesViewModel.sortAscending)
            binding.sortPanel.refresh()
            updateSortUi()
        }
        val newEditMode = !isEditMode
        isEditMode = newEditMode
        adapter.setEditMode(newEditMode)

        // Включаем/выключаем возможность перетаскивания
        if (isEditMode) {
            itemTouchHelper.attachToRecyclerView(binding.favoriteRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
           // saveOrderChanges()
        }
        updateStripIcons()
    }

    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте треки для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

}