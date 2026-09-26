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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentFoldersSelectionBinding
import com.example.muzpleer.ui.local.adapters.FoldersSelectionAdapter
import com.example.muzpleer.ui.local.frags.AlbumFragment.Companion.TAG
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataFolder

class FoldersSelectionFragment: Fragment() {
    private lateinit var binding: FragmentFoldersSelectionBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: FoldersSelectionAdapter
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
        binding = FragmentFoldersSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getLong("playlistId") ?: -1
        selectionType = arguments?.getSerializable("selectionType") as? SelectionType
            ?: SelectionType.ALL_SONGS
        
        viewModel.setAppBarTitle("Выбрать песни из папки")

        adapter = FoldersSelectionAdapter (viewModel){ folder ->

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("playlistId", playlistId)
                putString("folderPath", folder.path)
                putSerializable("selectionType", selectionType)
                Log.d(TAG,"FoldersSelectionFragment onViewCreated bundle: selectionType =$selectionType " +
                        "folderId = ${folder.id} playlistId = $playlistId ")
            }
            findNavController().navigate( R.id.songsSelectionFragment, bundle)
        }

        binding.folderSelectionRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FoldersSelectionFragment.adapter
        }

        viewModel.filteredFolders.observe(viewLifecycleOwner) { filteredFolders ->
            Log.d(TAG,"AlbumSelectionFragment onViewCreated filteredAlbums.observe: filteredFolders.size= ${filteredFolders.size} ")
            if (viewModel.getSongs().isEmpty()) binding.progressBarFolderSelection.visibility = View.VISIBLE else
                binding.progressBarFolderSelection.visibility = View.GONE
            if (filteredFolders.isEmpty()) binding.imageHolder3FolderSelection.visibility = View.VISIBLE else
                binding.imageHolder3FolderSelection.visibility = View.GONE
            val sortedData =getSortedDataFolder(filteredFolders)
            adapter.folders = sortedData  //передаём данные в адаптер
        }

        //восстанавливаем позицию списка после поворота или возвращения на экран
        binding.folderSelectionRecyclerView.layoutManager?.scrollToPosition(viewModel.getPositionFolder())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other3, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other3)
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
                viewModel.filterFolders(newText.orEmpty())
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

}