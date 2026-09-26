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
import com.example.muzpleer.databinding.FragmentArtistSelectionBinding
import com.example.muzpleer.ui.local.adapters.ArtistSelectionAdapter
import com.example.muzpleer.ui.local.frags.AlbumFragment.Companion.TAG
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataArtist

class ArtistsSelectionFragment: Fragment() {
    private lateinit var binding: FragmentArtistSelectionBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: ArtistSelectionAdapter
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
        binding = FragmentArtistSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getLong("playlistId") ?: -1
        selectionType = arguments?.getSerializable("selectionType") as? SelectionType
            ?: SelectionType.ALL_SONGS

        viewModel.setAppBarTitle("Выбрать песни исполнителя")

        adapter = ArtistSelectionAdapter (viewModel){ artist ->

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("playlistId", playlistId)
                putLong("artistId", artist.id)
                putSerializable("selectionType", selectionType)
                Log.d(TAG,"AlbumSelectionFragment onViewCreated bundle: selectionType =$selectionType " +
                        "artistId = ${artist.id} playlistId = $playlistId ")
            }
            findNavController().navigate( R.id.songsSelectionFragment, bundle)
        }

        binding.artistSelectionRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ArtistsSelectionFragment.adapter
        }

        viewModel.filteredArtists.observe(viewLifecycleOwner) { filteredArtists ->
            Log.d(TAG,"ArtistsSelectionFragment onViewCreated filteredAlbums.observe: filteredAlbums.size= ${filteredArtists.size} ")
            if (viewModel.getSongs().isEmpty()) binding.progressBarArtistSelection.visibility = View.VISIBLE else
                binding.progressBarArtistSelection.visibility = View.GONE
            if (filteredArtists.isEmpty()) binding.imageHolder3ArtistSelection.visibility = View.VISIBLE else
                binding.imageHolder3ArtistSelection.visibility = View.GONE
            val sortedData =getSortedDataArtist(filteredArtists)
            adapter.artists = sortedData  //передаём данные в адаптер
        }

        //восстанавливаем позицию списка после поворота или возвращения на экран
        binding.artistSelectionRecyclerView.layoutManager?.scrollToPosition(viewModel.getPositionArtist())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other3, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other3)
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
                viewModel.filterArtists(newText.orEmpty())
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

}