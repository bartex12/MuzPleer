package com.example.muzpleer.ui.local.frags

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
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentSingersBinding
import com.example.muzpleer.model.Artist
import com.example.muzpleer.model.Folder
import com.example.muzpleer.ui.local.adapters.ArtistsAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataSong
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ArtistsFragment:Fragment() {
    private var _binding: FragmentSingersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private lateinit var adapter: ArtistsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false
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
        _binding = FragmentSingersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Изначально показываем картинку-заглушку и крутилку
        showLoadingState()

        adapter = ArtistsAdapter(viewModel) { artist ->
            val playlist = getSortedDataSong(artist.songs)
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("artistId", artist.id)
                putString("artistName", artist.name)
                Log.d(TAG,"33 ArtistsFragment onViewCreated bundle: " +
                        "artistId = ${artist.id}  artistName = ${artist.name}  from = 3")
            }
            findNavController().navigate( R.id.songListArtistFragment,bundle)
        }

        binding.singersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ArtistsFragment.adapter
        }

        // Настраиваем ItemTouchHelper
        val callback = ItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)

        // Изначально НЕ прикрепляем - прикрепим только в режиме редактирования
        // itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)

        binding.singersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ArtistsFragment.adapter
        }

        viewModel.filteredArtists.observe(viewLifecycleOwner) { filteredArtists ->
            Log.d(TAG,"34 ArtistsFragment onViewCreated filteredArtists.observe: filteredArtists.size= ${filteredArtists.size} ")
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок
            adapter.artists = filteredArtists  //передаём данные в адаптер
            // Управляем видимостью в зависимости от состояния
            updateEmptyState(filteredArtists)
        }
        //восстанавливаем позицию списка после поворота или возвращения на экран
        binding.singersRecyclerView.layoutManager?.scrollToPosition(viewModel.getPositionArtist())
    }

    /**
     * Показать состояние загрузки: картинка + прогрессбар, текст скрыт.
     * Вызывается при открытии фрагмента.
     */
    private fun showLoadingState() {
        binding.imageHolder3Singers.visibility = View.VISIBLE
        binding.progressBarSingers.visibility = View.VISIBLE
        binding.tvEmptySinger.visibility = View.GONE
    }

    /**
     * Обновить состояние пустого списка.
     * @param playlists текущий список (после фильтрации).
     */
    private fun updateEmptyState(playlists: List<Artist>) {
        if (playlists.isEmpty()) {
            // Список пуст — скрываем прогрессбар, показываем картинку и текст
            binding.progressBarSingers.visibility = View.GONE
            binding.imageHolder3Singers.visibility = View.VISIBLE
            binding.tvEmptySinger.visibility = View.VISIBLE

            // Выбираем текст в зависимости от того, активен ли поиск
            binding.tvEmptySinger.text = if (isSearchActive) {
                "По вашему запросу ничего не найдено"
            } else {
                "У вас пока нет исполнителей"
            }
        } else {
            // Список не пуст — всё скрываем
            binding.progressBarSingers.visibility = View.GONE
            binding.imageHolder3Singers.visibility = View.GONE
            binding.tvEmptySinger.visibility = View.GONE
        }
    }


    override fun onResume() {
        super.onResume()
        // Сбрасываем режим редактирования при возвращении
        resetEditModeOnStart()
        // Обновляем меню
        requireActivity().invalidateOptionsMenu()
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()

        // Сбрасываем режим редактирования при уходе
        resetEditModeOnPause()

        //определяем первую видимую позицию
        val manager = binding.singersRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        viewModel.savePositionArtist(firstPosition)
        Log.d(TAG, "ArtistsFragment onPause firstPosition = $firstPosition")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): ArtistsFragment {
            this.viewPager = viewPager
            return ArtistsFragment()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other)
        val searchView =searchItem.actionView as SearchView
        //значок лупы слева в развёрнутом сост и сворачиваем строку поиска (true)
        searchView.setIconifiedByDefault(true)
        //пишем подсказку в строке поиска
        searchView.queryHint = getString(R.string.search_artist)
        //устанавливаем в панели действий кнопку ( > )для отправки поискового запроса
        searchView.isSubmitButtonEnabled = true
        //устанавливаем слушатель
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                // Устанавливаем флаг, что поиск активен, если есть текст
                isSearchActive = !newText.isNullOrEmpty()
                viewModel.filterArtists(newText.orEmpty())
                return true
            }
        })
        // Сбрасываем флаг поиска при закрытии SearchView
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchActive = false
                viewModel.filterArtists("") // сброс фильтра
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_go_to_song).isVisible =false

        // Настраиваем кнопку редактирования
        val editItem = menu.findItem(R.id.action_edit_order)
        // Меняем текст
        editItem.title = if (isEditMode) "Готово" else "Редактировать порядок"

        // Меняем цвет в зависимости от режима
        val color = if (isEditMode) {
            ContextCompat.getColor(requireContext(), R.color.colorSecondaryBlue)
        } else {
            ContextCompat.getColor(requireContext(), R.color.white)
        }
        editItem?.icon?.setTint(color)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_edit_order -> {
                toggleEditMode()
                viewModel.syncArtist ()
                true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        adapter.setEditMode(isEditMode)

        // Включаем/выключаем возможность перетаскивания
        if (isEditMode) {
            itemTouchHelper.attachToRecyclerView(binding.singersRecyclerView)
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
            adapter.onDrop()  // 🔧 СОХРАНЯЕМ ПРИ ВЫХОДЕ
        }

        // Обновляем меню
        activity?.invalidateOptionsMenu()

        // Показываем/скрываем подсказку
        if (isEditMode) {
            showEditModeHint()
        }
    }
    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте песни для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

    private fun resetEditModeOnStart() {
        // Сбрасываем режим редактирования при открытии фрагмента
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
            adapter.onDrop()  // 🔧 СОХРАНЯЕМ ПРИ СБРОСЕ
            Log.d(TAG, "PlaylistFragment: Режим редактирования сброшен при открытии")
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

}