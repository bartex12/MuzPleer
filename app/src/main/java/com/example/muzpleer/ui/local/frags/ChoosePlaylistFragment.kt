package com.example.muzpleer.ui.local.frags

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.R
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.databinding.FragmentChoosePlaylistBinding
import com.example.muzpleer.ui.local.adapters.PlaylistChooseAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ChoosePlaylistFragment :Fragment()  {

    private var _binding: FragmentChoosePlaylistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModel()
    private lateinit var adapter: PlaylistChooseAdapter
    private var songId: Long = -1L
    private var songTitle: String = ""

    companion object {
        const val TAG = "33333"
        const val TAG1 = "ChoosePlaylistFragment"
        private const val ARG_SONG_ID = "song_id"
        private const val ARG_SONG_TITLE = "song_title"

        fun newInstance(songId: Long, songTitle: String): ChoosePlaylistFragment {
            return ChoosePlaylistFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SONG_ID, songId)
                    putString(ARG_SONG_TITLE, songTitle)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔧 Получаем параметры из Bundle
        arguments?.let {
            songId = it.getLong(ARG_SONG_ID, -1L)
            songTitle = it.getString(ARG_SONG_TITLE, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChoosePlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, " //**// ChoosePlaylistFragment onViewCreated:  ")

        updateToolbarTitle("Выбор плейлиста")

        // Настройка адаптера
        adapter = PlaylistChooseAdapter(viewModel) { playlist ->
            viewModel.addSongToPlaylist(songId, playlist.id)
            Toast.makeText(
                requireContext(),
                "Песня \"$songTitle\" добавлена в \"${playlist.playlistName}\"",
                Toast.LENGTH_SHORT
            ).show()
            // Возвращаемся назад
            findNavController().navigateUp()
        }

        binding.rvPlaylists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChoosePlaylistFragment.adapter
        }

        // Наблюдаем за списком плейлистов с правильным LifecycleOwner
        viewModel.filteredPlaylists.observe(viewLifecycleOwner) { playlists ->
            adapter.playlists = playlists.filter { it.id != -1L }
        }

        viewModel.coverImageUriLevel.observe(viewLifecycleOwner) {uri->
            //последняя изменённая обложка плейлиста
            Log.d(TAG, " //**// ChoosePlaylistFragment onViewCreated:  uri = $uri")
        }

        // Кнопка закрытия
        binding.btnClose.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Сбрасываем заголовок при уходе с фрагмента
        (requireActivity() as? MainActivity)?.resetToTabTitle()
        _binding = null
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }
}