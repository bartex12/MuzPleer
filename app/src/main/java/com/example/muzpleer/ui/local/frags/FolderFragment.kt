package com.example.muzpleer.ui.local.frags

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentFoldersBinding
import com.example.muzpleer.model.Folder
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.adapters.FoldersAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.util.getSortedDataSong
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class FolderFragment : Fragment(){
    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private lateinit var adapter: FoldersAdapter
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
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Изначально показываем картинку-заглушку и крутилку
        showLoadingState()

        adapter = FoldersAdapter(viewModel, foldersViewModel){ folder ->
            val playlist = getSortedDataSong(folder.songs)
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putString("folderPath", folder.path)
                putString("folderName", folder.name)
            }
            findNavController().navigate( R.id.songListFolderFragment, bundle)
        }

        // Настраиваем ItemTouchHelper
        val callback = ItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)

        // Изначально НЕ прикрепляем - прикрепим только в режиме редактирования
        // itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)

        binding.foldersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FolderFragment.adapter
        }

        foldersViewModel.filteredFolders.observe(viewLifecycleOwner) { filteredFolders ->
            Log.d(TAG,"35 FolderFragment onViewCreated filteredFolders.observe: filteredFolders.size= ${filteredFolders.size} ")
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок
            adapter.folders = filteredFolders  //передаём данные в адаптер
            // Управляем видимостью в зависимости от состояния
            updateEmptyState(filteredFolders)
        }
        //восстанавливаем позицию списка после поворота или возвращения на экран
        binding.foldersRecyclerView.layoutManager?.scrollToPosition(foldersViewModel.getPositionFolder())
    }

    /**
     * Показать состояние загрузки: картинка + прогрессбар, текст скрыт.
     * Вызывается при открытии фрагмента.
     */
    private fun showLoadingState() {
        binding.imageHolder3Folder.visibility = View.VISIBLE
        binding.progressBarFolder.visibility = View.VISIBLE
        binding.tvEmptyFolder.visibility = View.GONE
    }

    /**
     * Обновить состояние пустого списка.
     * @param playlists текущий список (после фильтрации).
     */
    private fun updateEmptyState(playlists: List<Folder>) {
        if (playlists.isEmpty()) {
            // Список пуст — скрываем прогрессбар, показываем картинку и текст
            binding.progressBarFolder.visibility = View.GONE
            binding.imageHolder3Folder.visibility = View.VISIBLE
            binding.tvEmptyFolder.visibility = View.VISIBLE

            // Выбираем текст в зависимости от того, активен ли поиск
            binding.tvEmptyFolder.text = if (isSearchActive) {
                "По вашему запросу ничего не найдено"
            } else {
                "У вас пока нет папок"
            }
        } else {
            // Список не пуст — всё скрываем
            binding.progressBarFolder.visibility = View.GONE
            binding.imageHolder3Folder.visibility = View.GONE
            binding.tvEmptyFolder.visibility = View.GONE
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
        val manager = binding.foldersRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        foldersViewModel.savePositionFolder(firstPosition)
        Log.d(TAG, "FolderFragment onPause firstPosition = $firstPosition")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun resetEditModeOnStart() {
        //  Сбрасываем режим редактирования при открытии фрагмента
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
            Log.d(TAG, "PlaylistFragment: Режим редактирования сброшен при открытии")
        }
    }

    private fun resetEditModeOnPause() {
        // Сбрасываем режим редактирования при уходе с фрагмента
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)

            // Сохраняем изменения порядка
            adapter.onDrop() // Сохраняем порядок в БД

            Log.d(TAG, "PlaylistFragment: Режим редактирования сброшен при уходе")
        }
    }

    companion object {
        const val TAG = "33333"
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): FolderFragment {
            this.viewPager = viewPager
            return FolderFragment()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other)
        val searchView =searchItem.actionView as SearchView

        //значок лупы слева в развёрнутом сост и сворачиваем строку поиска (true)
        searchView.setIconifiedByDefault(true)
        //пишем подсказку в строке поиска
        searchView.queryHint = getString(R.string.search_folder)
        //устанавливаем в панели действий кнопку ( > )для отправки поискового запроса
        //searchView.isSubmitButtonEnabled = true
        //устанавливаем слушатель
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                // Устанавливаем флаг, что поиск активен, если есть текст
                isSearchActive = !newText.isNullOrEmpty()
                foldersViewModel.filterFolders(newText.orEmpty())
                return true
            }
        })
        // Сбрасываем флаг поиска при закрытии SearchView
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchActive = false
                foldersViewModel.filterFolders("") // сброс фильтра
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
            itemTouchHelper.attachToRecyclerView(binding.foldersRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
        }

        // Обновляем меню
        activity?.invalidateOptionsMenu()
    }
    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте треки для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

}