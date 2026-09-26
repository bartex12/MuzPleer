package com.example.muzpleer.ui.local.frags

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentSongsSelectionBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.adapters.SongSelectionAdapter
import com.example.muzpleer.ui.local.frags.SongListFolderFragment.Companion.TAG
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.toast
import com.google.android.material.textfield.TextInputLayout

class SongsSelectionFragment : Fragment() {
    private lateinit var binding: FragmentSongsSelectionBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: SongSelectionAdapter
    private var playlistId: Long = -1
    private var selectionType: SelectionType = SelectionType.ALL_SONGS
    private var title = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSongsSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getLong("playlistId") ?: -1
        selectionType = arguments?.getSerializable("selectionType") as? SelectionType
            ?: SelectionType.ALL_SONGS

        when (selectionType) {
            SelectionType.ALL_SONGS -> {
                viewModel.loadAllSongsForAdding()
                title = "Все песни"
            }
            SelectionType.FAVORITES ->{
                viewModel.loadFavoritesSongsForAdding()
                title = "Избранное"
            }
            SelectionType.ALBUM ->{
                val albumId = arguments?.getLong("albumId") ?: -1
                viewModel.loadAlbumSongsForAdding(albumId)
            }
            SelectionType.ARTIST ->{
                val artistId = arguments?.getLong("artistId") ?: -1
                viewModel.loadArtistSongsForAdding(artistId)
            }
            SelectionType.FOLDER ->{
                val folderPath = arguments?.getString("folderPath") ?: ""
                viewModel.loadFolderSongsForAdding(folderPath)
            }

            SelectionType.PLAYLISTS -> {
                val selectedPlaylistId =  arguments?.getLong("selectedPlaylistId") ?: -1
                viewModel.loadPlaylistSongsForAdding(selectedPlaylistId)
            }
        }
        setupRecyclerView()
        setupObservers()
        setupButtons()
        setupSearch()

        viewModel.filteredListSelectedSong.observe (viewLifecycleOwner){selectedSongs->
            Log.d(TAG, "##$$$## SongsSelectionFragment observe selectedSongs.size = ${selectedSongs.size}")
            adapter.data = selectedSongs
        }
    }

    private fun setupRecyclerView() {
        adapter = SongSelectionAdapter(
            sharedViewModel = viewModel,
        onSelectionChanged =  { selectedSongs ->
            updateSelectionCount(selectedSongs.size)
            updateAddButtonState(selectedSongs.isNotEmpty())
            }
        )
        binding.songsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.songsRecyclerView.adapter = adapter
    }

    private fun setupObservers() {
        when (selectionType) {
            SelectionType.ALL_SONGS -> {
                viewModel.songs.observe(viewLifecycleOwner) { songs ->
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs
                    //adapter.data = getSortedDataSong(songs)
                    updateSelectionCount(0)
                }
            }
            SelectionType.FAVORITES -> {
                viewModel.favoriteSongs.observe(viewLifecycleOwner) { songs ->
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs  //не сортируем, чтобы менять порядок
                    updateSelectionCount(0)
                }
            }
            SelectionType.ALBUM ->{
                viewModel.listAlbumSong.observe(viewLifecycleOwner) { songs ->
                    title = "Выберите песни"
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs
                    //adapter.data = getSortedDataSong(songs)
                    updateSelectionCount(0)
                }
            }
            SelectionType.ARTIST ->{
                viewModel.listArtistSong.observe(viewLifecycleOwner) { songs ->
                    title = "Выберите песни"
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs
                    //adapter.data = getSortedDataSong(songs)
                    updateSelectionCount(0)
                }
            }
            SelectionType.FOLDER ->{
                viewModel.listFolderSong.observe(viewLifecycleOwner) { folderSongs ->
                    title = "Выберите песни"
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(folderSongs)
                    adapter.data = folderSongs
                    //adapter.data = getSortedDataSong(folderSongs)
                    updateSelectionCount(0)
                }
            }

            SelectionType.PLAYLISTS -> {
               viewModel.currentPlaylistSongs.observe(viewLifecycleOwner) {playlistSong->
                   title = "Выберите песни"
                   viewModel.setAppBarTitle(title)
                   viewModel.setListSelectedSong(playlistSong)
                   if (playlistSong != null) {
                       adapter.data = playlistSong
                   } else {
                       adapter.data = emptyList<Song>()
                   }
               }
            }
        }
    }

    private fun setupButtons() {
        binding.selectAllButton.setOnClickListener {
            adapter.selectAll()
        }

        binding.addButton.setOnClickListener {
            addSelectedSongsToPlaylist()
        }
    }

    private fun setupSearch() {
        // Устанавливаем слушатель на иконку поиска
        binding.inputLayoutSearchSelection.setEndIconOnClickListener {
            performSearch()
        }

        // Устанавливаем слушатель на нажатие Enter на клавиатуре
        binding.inputEditTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }

        // Устанавливаем TextWatcher для поиска при вводе текста
        binding.inputEditTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Не нужно
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Ищем при каждом изменении текста
                s?.let { query ->
                    viewModel.filterListSelectedSongs(query.toString())
                    Log.d(TAG, "##$$$## SongsSelectionFragment onTextChanged query = $query")
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotEmpty() == true
                binding.inputLayoutSearchSelection.endIconMode =
                    if (hasText) TextInputLayout.END_ICON_CLEAR_TEXT
                    else TextInputLayout.END_ICON_CUSTOM
                binding.inputLayoutSearchSelection.setEndIconDrawable(
                    if (hasText) R.drawable.baseline_close_24
                    else R.drawable.baseline_search_24
                )
            }
        })

        // Очистка по нажатию на иконку
        binding.inputLayoutSearchSelection.setEndIconOnClickListener {
            if (binding.inputEditTextSearch.text?.isNotEmpty() == true) {
                binding.inputEditTextSearch.text?.clear()
                viewModel.filterListSelectedSongs("")
            } else {
                performSearch()
            }
        }

        // Перехватываем нажатие кнопки "Назад" для поля ввода
        setupBackButtonHandler()
    }

    // Перехватываем события клавиатуры для кнопки "Назад"
    private fun setupBackButtonHandler() {
        binding.inputEditTextSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.inputEditTextSearch.hasFocus()) {
                    // Скрываем клавиатуру и убираем фокус ТОЛЬКО при нажатии Back
                    hideKeyboardAndClearFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun hideKeyboardAndClearFocus() {
        // Убираем фокус с поля
        binding.inputEditTextSearch.clearFocus()

        // Гарантированно скрываем курсор
        binding.inputEditTextSearch.isCursorVisible = false

        //стираем текст в поле ввода и показываем весь список
        if (binding.inputEditTextSearch.text?.isNotEmpty() == true) {
            binding.inputEditTextSearch.text?.clear()
            viewModel.filterListSelectedSongs("")
        }
        // Скрываем клавиатуру
        hideKeyboard()
        Log.d(TAG, "SongsSelectionFragment hideKeyboardAndClearFocus: фокус снят, курсор скрыт")
    }

    private fun performSearch() {
        val query = binding.inputEditTextSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            viewModel.filterListSelectedSongs(query)
            hideKeyboard()
        } else {
            toast("Введите текст для поиска")
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputEditTextSearch.windowToken, 0)
    }

    private fun updateSelectionCount(count: Int) {
        binding.selectionCount.text = "Выбрано: $count"
    }

    private fun updateAddButtonState(isEnabled: Boolean) {
        binding.addButton.isEnabled = isEnabled
    }

    private fun addSelectedSongsToPlaylist() {
        val selectedSongs = adapter.getSelectedSongs()
        if (selectedSongs.isNotEmpty()) {
            viewModel.addSongsToPlaylistWhithSongs(playlistId, selectedSongs)
            findNavController().navigateUp() // Вернуться к выбору источника, если песни или избранное
            if(selectionType == SelectionType.ALBUM || selectionType == SelectionType.ARTIST ||
                selectionType == SelectionType.FOLDER){
                findNavController().navigateUp()
            }
        }else {
            Toast.makeText(requireContext(), "Выберите песни для добавления", Toast.LENGTH_SHORT).show()
        }
    }


}