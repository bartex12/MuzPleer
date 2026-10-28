package com.example.muzpleer.ui.local.frags.playlist

import android.graphics.Color
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputLayout
import com.example.muzpleer.ui.local.viewmodel.filterSongsByQuery
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentRemoveSongsFromPlaylistBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.adapters.SongSelectionAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.util.toast
import com.google.android.material.button.MaterialButton


//класс для удаления песен из плейлиста с экрана списка плейлистов
class RemoveSongsFromPlaylistFragment : Fragment() {

    private var _binding: FragmentRemoveSongsFromPlaylistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val args: RemoveSongsFromPlaylistFragmentArgs by navArgs()

    private lateinit var adapter: SongSelectionAdapter
    private var playlistId: Long = 0
    private var playlistName: String = ""
    private var allSongs: List<Song> = emptyList() //полный список треков плейлиста (без фильтра)

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

        viewModel.setAppBarTitle("Удалить треки из плейлиста: $playlistName") //заголовок тулбара

        setupRecyclerView()
        loadPlaylistSongs()
        setupButtons()
        setupSearch()
    }

    private fun setupRecyclerView() {
        adapter = SongSelectionAdapter(
            sharedViewModel = viewModel,
            onSelectionChanged = { selectedSongs ->
                // Обновляем текст кнопки
                val count = selectedSongs.size
                binding.btnDeleteSelected.text =
                    if (count > 0) "Удалить($count)" else "Удалить"
                binding.btnDeleteSelected.isEnabled = count > 0
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadPlaylistSongs() {
        playlistsViewModel.loadCurrentPlaylistForSongs(playlistId)

        playlistsViewModel.currentPlaylistSongs.observe(viewLifecycleOwner) { songs ->
            if (songs != null) {
                allSongs = songs
                applyFilter()
                binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
                binding.btnSelectAll.isEnabled = songs.isNotEmpty()
            }
        }
    }

    private fun applyFilter() {
        val query = binding.inputEditTextSearch.text?.toString()?.trim().orEmpty()
        adapter.data = if (query.isEmpty()) allSongs else allSongs.filterSongsByQuery(query)
    }

    private fun setupSearch() {
        binding.inputEditTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotEmpty() == true
                binding.inputLayoutSearchSelection.endIconMode =
                    if (hasText) TextInputLayout.END_ICON_CLEAR_TEXT
                    else TextInputLayout.END_ICON_CUSTOM
                binding.inputLayoutSearchSelection.setEndIconDrawable(
                    if (hasText) R.drawable.baseline_close_24 else R.drawable.baseline_search_24
                )
            }
        })
        binding.inputEditTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else false
        }
        binding.inputLayoutSearchSelection.setEndIconOnClickListener {
            if (binding.inputEditTextSearch.text?.isNotEmpty() == true) {
                binding.inputEditTextSearch.text?.clear()
            } else {
                hideKeyboard()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputEditTextSearch.windowToken, 0)
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
                toast("Выберите треки для удаления")
            }
        }

        binding.btnSelectAll.isEnabled = adapter.data.isNotEmpty()
        binding.btnDeleteSelected.isEnabled = false
    }

    private fun showDeleteConfirmationDialog(selectedSongs: List<Song>) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_songs, null)

        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDeleteSongsMessage)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnDeleteSongsConfirm)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDeleteSongsCancel)

        // Подставляем количество и название плейлиста
        //tvMessage.text = "Удалить ${selectedSongs.size} песен из плейлиста \"$playlistName\"?"
        tvMessage.text = resources.getQuantityString(
            R.plurals.delete_songs_message,
            selectedSongs.size,
            selectedSongs.size,
            playlistName
        )

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Прозрачный фон окна — чтобы не было белой подложки вокруг нашего макета
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        btnConfirm.setOnClickListener {
            val songIds = selectedSongs.map { it.id }
            dialog.dismiss()
            //уходим с экрана только после удаления: иначе экран, куда вернулись, прочитает список посреди удаления
            val navController = findNavController()
            playlistsViewModel.removeSongsFromPlaylist(playlistId, songIds) {
                if (isAdded) navController.navigateUp()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

//    private fun showDeleteConfirmationDialog(selectedSongs: List<Song>) {
//        AlertDialog.Builder(requireContext())
//            .setTitle("Удаление песен")
//            .setMessage("Удалить ${selectedSongs.size} песен из плейлиста \"$playlistName\"?")
//            .setPositiveButton("Удалить") { _, _ ->
//                val songIds = selectedSongs.map { it.id }
//                playlistsViewModel.removeSongsFromPlaylist(playlistId, songIds)
//                findNavController().navigateUp()
//            }
//            .setNegativeButton("Отмена", null)
//            .show()
//    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
    }

}