package com.example.muzpleer.ui.local.frags.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.databinding.FragmentRemoveSongsFromPlaylistBinding
import com.example.muzpleer.ui.local.adapters.SongSelectionAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.toast


//класс для удаления песен из плейлиста с экрана списка плейлистов
class RemoveSongsFromPlaylistFragment : Fragment() {

    private var _binding: FragmentRemoveSongsFromPlaylistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private val args: RemoveSongsFromPlaylistFragmentArgs by navArgs()

    private lateinit var adapter: SongSelectionAdapter
    private var playlistId: Long = 0
    private var playlistName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoveSongsFromPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = args.playlistId
        playlistName = args.playlistName

        viewModel.setAppBarTitle("Удалить песни из плейлиста: $playlistName") //заголовок тулбара

        setupRecyclerView()
        loadPlaylistSongs()
        setupButtons()
    }

    private fun setupRecyclerView() {
        adapter = SongSelectionAdapter(
            sharedViewModel = viewModel,
            onSelectionChanged = { selectedSongs ->
                // Обновляем текст кнопки
                val count = selectedSongs.size
                binding.btnDeleteSelected.text =
                    if (count > 0) "Удалить выбранное ($count)" else "Удалить выбранное"
                binding.btnDeleteSelected.isEnabled = count > 0
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadPlaylistSongs() {
        viewModel.loadCurrentPlaylistForSongs(playlistId)

        viewModel.currentPlaylistSongs.observe(viewLifecycleOwner) { songs ->
            if (songs != null) {
                adapter.data = songs
                binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
                binding.btnSelectAll.isEnabled = songs.isNotEmpty()
            }
        }
    }

    private fun setupButtons() {
        binding.btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selectedSongs = adapter.getSelectedSongs()
            if (selectedSongs.isNotEmpty()) {
                showDeleteConfirmationDialog(selectedSongs)
            } else {
                toast("Выберите песни для удаления")
            }
        }

        binding.btnSelectAll.isEnabled = adapter.data.isNotEmpty()
        binding.btnDeleteSelected.isEnabled = false
    }

    private fun showDeleteConfirmationDialog(selectedSongs: List<com.example.muzpleer.model.Song>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление песен")
            .setMessage("Удалить ${selectedSongs.size} песен из плейлиста \"$playlistName\"?")
            .setPositiveButton("Удалить") { _, _ ->
                val songIds = selectedSongs.map { it.id }
                viewModel.removeSongsFromPlaylist(playlistId, songIds)
                findNavController().navigateUp()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
    }

}