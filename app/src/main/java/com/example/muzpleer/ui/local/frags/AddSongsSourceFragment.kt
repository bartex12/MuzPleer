package com.example.muzpleer.ui.local.frags

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.muzpleer.databinding.FragmentAddSongsSourceBinding
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel

//экран выбора источника для замены обложки
class AddSongsSourceFragment : Fragment() {
    private lateinit var binding: FragmentAddSongsSourceBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private var playlistId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddSongsSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Для Safe Args
        val args: AddSongsSourceFragmentArgs by navArgs()
        playlistId = args.playlistId
        Log.d(TAG, "*!!!* AddSongsSourceFragment onViewCreated playlistId = $playlistId  ")
        //playlistId = arguments?.getLong("playlistId") ?: -1   //если не через Safe Args

        viewModel.setAppBarTitle("Выбор источника")

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.allSongsOption.setOnClickListener {
            navigateToSongsSelection(SelectionType.ALL_SONGS)
        }

        binding.albumsOption.setOnClickListener {
            navigateToAlbumsSelection()
        }

        binding.artistsOption.setOnClickListener {
            navigateToArtistsSelection()
        }

        binding.foldersOption.setOnClickListener {
            navigateToFoldersSelection()
        }

        binding.favoritesOption.setOnClickListener {
            navigateToSongsSelection(SelectionType.FAVORITES)
        }

        binding.playlistsOption.setOnClickListener {
            navigateToPlaylistsSelection()
        }

    }

    private fun navigateToSongsSelection(selectionType: SelectionType) {
        val action = AddSongsSourceFragmentDirections.actionAddSongsSourceToSongsSelection(
            playlistId = playlistId,
            selectionType = selectionType
        )
        findNavController().navigate(action)
    }

    private fun navigateToAlbumsSelection() {
        val action = AddSongsSourceFragmentDirections.actionAddSongsSourceToAlbumsSelection(
            playlistId = playlistId,
            selectionType = SelectionType.ALBUM
        )
        findNavController().navigate(action)
    }

    private fun navigateToArtistsSelection() {
        val action = AddSongsSourceFragmentDirections.actionAddSongsSourceToArtistsSelection(
            playlistId = playlistId,
            selectionType = SelectionType.ARTIST
        )
        findNavController().navigate(action)
    }

    private fun navigateToFoldersSelection() {
        val action = AddSongsSourceFragmentDirections.actionAddSongsSourceToFoldersSelection(
            playlistId = playlistId,
            selectionType = SelectionType.FOLDER
        )
        findNavController().navigate(action)
    }

    private fun navigateToPlaylistsSelection(){
        val action = AddSongsSourceFragmentDirections.actionAddSongsSourceFragmentToPlaylistsSelectionFragment(
            playlistId = playlistId, // id плейлиста, в который добавляем песни
            selectionType = SelectionType.PLAYLISTS
        )
        findNavController().navigate(action)
    }

    companion object{
        const val TAG = "33333"
    }
}


enum class SelectionType {
    ALL_SONGS, FAVORITES, ALBUM, ARTIST, FOLDER, PLAYLISTS
}