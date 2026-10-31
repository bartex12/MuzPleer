package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.panelColor
import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import com.example.muzpleer.util.formatDuration
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
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.local.viewmodel.ArtistsViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import com.example.muzpleer.ui.local.viewmodel.AlbumsViewModel
import com.example.muzpleer.util.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class SongsSelectionFragment : Fragment() {
    private lateinit var binding: FragmentSongsSelectionBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private val artistsViewModel: ArtistsViewModel by activityViewModel()
    private val albumsViewModel: AlbumsViewModel by activityViewModel()
    private lateinit var adapter: SongSelectionAdapter
    private var playlistId: Long = -1
    private var selectionType: SelectionType = SelectionType.ALL_SONGS
    private var title = ""
    private var timeToast: Toast? = null
    private var limitWarned = false

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
                title = "Все треки"
            }
            SelectionType.FAVORITES ->{
                favoritesViewModel.loadFavoritesSongsForAdding()
                title = "Избранное"
            }
            SelectionType.ALBUM ->{
                val albumId = arguments?.getLong("albumId") ?: -1
                albumsViewModel.loadAlbumSongsForAdding(albumId)
            }
            SelectionType.ARTIST ->{
                val artistId = arguments?.getLong("artistId") ?: -1
                artistsViewModel.loadArtistSongsForAdding(artistId)
            }
            SelectionType.FOLDER ->{
                val folderPath = arguments?.getString("folderPath") ?: ""
                foldersViewModel.loadFolderSongsForAdding(folderPath)
            }

            SelectionType.PLAYLISTS -> {
                val selectedPlaylistId =  arguments?.getLong("selectedPlaylistId") ?: -1
                playlistsViewModel.loadPlaylistSongsForAdding(selectedPlaylistId)
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
            updateAddButton(selectedSongs)
            checkTimeLimit(selectedSongs)
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
                    //updateSelectionCount(0)
                }
            }
            SelectionType.FAVORITES -> {
                favoritesViewModel.favoriteSongs.observe(viewLifecycleOwner) { songs ->
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs  //не сортируем, чтобы менять порядок
                    //updateSelectionCount(0)
                }
            }
            SelectionType.ALBUM ->{
                albumsViewModel.listAlbumSong.observe(viewLifecycleOwner) { songs ->
                    title = "Выберите треки"
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs
                    //adapter.data = getSortedDataSong(songs)
                    //updateSelectionCount(0)
                }
            }
            SelectionType.ARTIST ->{
                artistsViewModel.listArtistSong.observe(viewLifecycleOwner) { songs ->
                    title = "Выберите треки"
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(songs)
                    adapter.data = songs
                    //adapter.data = getSortedDataSong(songs)
                    //updateSelectionCount(0)
                }
            }
            SelectionType.FOLDER ->{
                foldersViewModel.listFolderSong.observe(viewLifecycleOwner) { folderSongs ->
                    title = "Выберите треки"
                    viewModel.setAppBarTitle(title)
                    viewModel.setListSelectedSong(folderSongs)
                    adapter.data = folderSongs
                    //adapter.data = getSortedDataSong(folderSongs)
                    //updateSelectionCount(0)
                }
            }

            SelectionType.PLAYLISTS -> {
               playlistsViewModel.currentPlaylistSongs.observe(viewLifecycleOwner) {playlistSong->
                   title = "Выберите треки"
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

//    private fun updateSelectionCount(count: Int) {
//        binding.selectionCount.text = "Выбрано: $count"
//    }

    private fun updateAddButton(selectedSong:List<Song>){
        val count = selectedSong.size
        val isEnable = count > 0
        val btnAdd = binding.root.findViewById<MaterialButton>(R.id.addButton)
        btnAdd.isEnabled = isEnable
        btnAdd.text =  if (count > 0) "Добавить($count)" else "Добавить"

        if(isEnable){
            btnAdd.setStrokeColorResource(R.color.white)
        }else{
            btnAdd.strokeColor = android.content.res.ColorStateList.valueOf(requireContext().panelColor())
        }
    }

    private fun updateAddButtonState(isEnabled: Boolean) {

        binding.addButton.isEnabled = isEnabled
        val btnAdd = binding.root.findViewById<MaterialButton>(R.id.addButton)
        if(isEnabled){
            btnAdd.setStrokeColorResource(R.color.white)
        }else{
            btnAdd.strokeColor = android.content.res.ColorStateList.valueOf(requireContext().panelColor())
        }
    }

    //Режим «Добавить треки по времени»: показываем суммарное время и предупреждаем о превышении
    private fun checkTimeLimit(selectedSongs: List<Song>) {
        val limitMs = playlistsViewModel.addByTimeLimitMs ?: return
        val playlist = playlistsViewModel.playlists.value?.firstOrNull { it.id == playlistId }
        val existingIds = playlist?.playlistSongs?.map { it.id }?.toSet() ?: emptySet()
        val existingMs = playlist?.playlistSongs?.sumOf { it.duration.coerceAtLeast(0L) } ?: 0L
        //треки, которые уже есть в плейлисте, второй раз не добавятся и время не увеличивают
        val newMs = selectedSongs.filter { it.id !in existingIds }
            .sumOf { it.duration.coerceAtLeast(0L) }
        val totalMs = existingMs + newMs

        timeToast?.cancel()
        timeToast = Toast.makeText(
            requireContext().applicationContext,
            "Суммарное время ${totalMs.formatDuration()}",
            Toast.LENGTH_SHORT
        ).also { it.show() }

        if (totalMs <= limitMs) {
            limitWarned = false
        } else if (!limitWarned) {
            limitWarned = true
            showLimitExceededDialog(limitMs, totalMs)
        }
    }

    private fun showLimitExceededDialog(limitMs: Long, totalMs: Long) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_playlist, null)
        dialogView.findViewById<TextView>(R.id.tvDeleteTitle).text =
            "Суммарное время треков превышает заданное"
        dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "Заданное: ${limitMs.formatDuration()}, сейчас: ${totalMs.formatDuration()}"
        val btnStop = dialogView.findViewById<MaterialButton>(R.id.btnDeleteYes)
        val btnContinue = dialogView.findViewById<MaterialButton>(R.id.btnDeleteNo)
        btnStop.text = "Закончить"
        btnContinue.text = "Продолжить"

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        btnContinue.setOnClickListener { dialog.dismiss() }
        btnStop.setOnClickListener {
            dialog.dismiss()
            stopAndOpenPlaylist()
        }
        dialog.show()
    }

    //Закончить: добавляем выбранные треки и переходим в плейлист
    private fun stopAndOpenPlaylist() {
        val selectedSongs = adapter.getSelectedSongs()
        val navController = findNavController()
        val name = playlistsViewModel.playlists.value
            ?.firstOrNull { it.id == playlistId }?.playlistName ?: ""
        val id = playlistId
        playlistsViewModel.addByTimeLimitMs = null
        playlistsViewModel.addSongsToPlaylistWhithSongs(id, selectedSongs) {
            if (navController.popBackStack(R.id.tabLocalFragment, false)) {
                navController.navigate(
                    R.id.songPlaylistFragment,
                    bundleOf("playlistId" to id, "playlistName" to name)
                )
            }
        }
    }

    private fun addSelectedSongsToPlaylist() {
        val selectedSongs = adapter.getSelectedSongs()
        if (selectedSongs.isNotEmpty()) {
            //заданное время не сбрасываем: пользователь может продолжить выбор из другого источника
            playlistsViewModel.addSongsToPlaylistWhithSongs(playlistId, selectedSongs)
            findNavController().navigateUp() // Вернуться к выбору источника, если песни или избранное
            if(selectionType == SelectionType.ALBUM || selectionType == SelectionType.ARTIST ||
                selectionType == SelectionType.FOLDER){
                findNavController().navigateUp()
            }
        }else {
            Toast.makeText(requireContext(), "Выберите треки для добавления", Toast.LENGTH_SHORT).show()
        }
    }


}