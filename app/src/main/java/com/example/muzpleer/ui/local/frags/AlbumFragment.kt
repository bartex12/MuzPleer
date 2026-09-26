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
import com.example.muzpleer.databinding.FragmentAlbumBinding
import com.example.muzpleer.ui.local.adapters.AlbumsAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataSong
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.activityViewModel


class AlbumFragment: Fragment() {
    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private lateinit var adapter: AlbumsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AlbumsAdapter (viewModel){ album ->
            val playlist = getSortedDataSong(album.songs)
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("albumId", album.albumId)
                putString("albumName", album.title)
                Log.d(TAG,"33 AlbumFragment onViewCreated bundle: " +
                        "albumId = ${album.albumId} albumName = ${album.title} from = 2 ")
            }
            findNavController().navigate( R.id.songListAlbumFragment, bundle)
        }

        // Настраиваем ItemTouchHelper
        val callback = ItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)

        // Изначально НЕ прикрепляем - прикрепим только в режиме редактирования
        // itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)

        binding.albumRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AlbumFragment.adapter
        }

        //viewModel.loadAlbums()  виснет приложение

        viewModel.filteredAlbums.observe(viewLifecycleOwner) { filteredAlbums ->
            Log.d(TAG,"33 AlbumFragment onViewCreated filteredAlbums.observe: filteredAlbums.size= ${filteredAlbums.size} ")
            if (viewModel.getSongs().isEmpty()) binding.progressBarAlbum.visibility = View.VISIBLE else binding.progressBarAlbum.visibility = View.GONE
            if (filteredAlbums.isEmpty()) binding.imageHolder3Album.visibility = View.VISIBLE else binding.imageHolder3Album.visibility = View.GONE
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок
            adapter.albums = filteredAlbums  //передаём данные в адаптер
        }
        //восстанавливаем позицию списка после поворота или возвращения на экран
        binding.albumRecyclerView.layoutManager?.scrollToPosition(viewModel.getPositionAlbum())
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
        val manager = binding.albumRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        Log.d(TAG, "AlbumFragment onPause firstPosition = $firstPosition")
        viewModel.savePositionAlbum(firstPosition)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): AlbumFragment {
            this.viewPager = viewPager
            return AlbumFragment()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other)
        val searchView =searchItem.actionView as SearchView
        //значок лупы слева в развёрнутом сост и сворачиваем строку поиска (true)
        searchView.setIconifiedByDefault(true)
        //пишем подсказку в строке поиска
        searchView.queryHint = getString(R.string.search_album)
        //устанавливаем в панели действий кнопку ( > )для отправки поискового запроса
        searchView.isSubmitButtonEnabled = true
        //устанавливаем слушатель
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterAlbums(newText.orEmpty())
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
                viewModel.syncAlbums()
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
            itemTouchHelper.attachToRecyclerView(binding.albumRecyclerView)
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
        }

        // Обновляем меню
        activity?.invalidateOptionsMenu()

        // Показываем/скрываем подсказку
        if (isEditMode) {
            showEditModeHint()
        }
    }
    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте альбомы для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
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
        //  Сбрасываем режим редактирования при уходе с фрагмента
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)

            // Сохраняем изменения порядка
            adapter.onDrop() // Сохраняем порядок в БД

            Log.d(TAG, "PlaylistFragment: Режим редактирования сброшен при уходе")
        }
    }
}