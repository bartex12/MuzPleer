package com.example.muzpleer.ui.local.frags

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentAlbumSelectionBinding
import com.example.muzpleer.ui.local.adapters.AlbumsSelectionAdapter
import com.example.muzpleer.ui.local.frags.AlbumFragment.Companion.TAG
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataAlbum

class AlbumSelectionFragment: Fragment() {
    private lateinit var binding: FragmentAlbumSelectionBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: AlbumsSelectionAdapter
    private var playlistId: Long = -1
    private var selectionType: SelectionType = SelectionType.ALL_SONGS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAlbumSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getLong("playlistId") ?: -1
        selectionType = arguments?.getSerializable("selectionType") as? SelectionType
            ?: SelectionType.ALL_SONGS

        viewModel.setAppBarTitle("Выбрать песни из альбома")

        adapter = AlbumsSelectionAdapter (viewModel){ album ->

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("playlistId", playlistId)
                putLong("albumId", album.albumId)
                putSerializable("selectionType", selectionType)
                Log.d(TAG,"AlbumSelectionFragment onViewCreated bundle: selectionType =$selectionType " +
                        "albumId = ${album.id} playlistId = $playlistId ")
            }
            findNavController().navigate( R.id.songsSelectionFragment, bundle)
        }

        binding.albumSelectionRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AlbumSelectionFragment.adapter
        }

        viewModel.filteredAlbums.observe(viewLifecycleOwner) { filteredAlbums ->
            Log.d(TAG,"AlbumSelectionFragment onViewCreated filteredAlbums.observe: filteredAlbums.size= ${filteredAlbums.size} ")
            if (viewModel.getSongs().isEmpty()) binding.progressBarAlbumSelection.visibility = View.VISIBLE else
                binding.progressBarAlbumSelection.visibility = View.GONE
            if (filteredAlbums.isEmpty()) binding.imageHolder3AlbumSelection.visibility = View.VISIBLE else
                binding.imageHolder3AlbumSelection.visibility = View.GONE
            val sortedData =getSortedDataAlbum(filteredAlbums)
            adapter.albums = sortedData  //передаём данные в адаптер
        }

        //восстанавливаем позицию списка после поворота или возвращения на экран
        binding.albumSelectionRecyclerView.layoutManager?.scrollToPosition(viewModel.getPositionAlbum())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other3, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other3)
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
}