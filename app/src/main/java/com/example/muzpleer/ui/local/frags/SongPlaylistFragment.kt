package com.example.muzpleer.ui.local.frags

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentAlltracksForPlaylistBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.SongsPlaylistAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.player.PlayerViewModel
import com.example.muzpleer.util.getNormalizedPath
import com.example.muzpleer.util.toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SongPlaylistFragment:Fragment() {
    private var _binding: FragmentAlltracksForPlaylistBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongsPlaylistAdapter
    private val viewModel: SharedViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false
    private  var playlistId:Long = -1
    private var totalSongsCount = 0  // Храним общее количество песен в плейлисте(не отфильтрованных)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(arguments?.getLong("playlistId") != null) {
            playlistId = requireArguments().getLong("playlistId")
            Log.d(TAG, "!@#@ SongPlaylistFragment onCreate playlistId  $playlistId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlltracksForPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сброс режима редактирования при каждом открытии фрагмента
        resetEditModeOnStart()

        // Получаем название плейлиста из аргументов или ViewModel
        val playlistName = arguments?.getString("playlistName")
            ?: viewModel.getCurrentPlaylist()?.playlistName

        // Устанавливаем заголовок
        updateToolbarTitle("Плейлист: $playlistName")

        adapter = SongsPlaylistAdapter(viewModel) { song ->
            val playlistSongs=
                viewModel.getCurrentPlaylist()?.playlistSongs ?: listOf()
            //можно было взять и из currentFilteredPlaylistSongs
            Log.d(
                TAG, "!@#@ SongPlaylistFragment размер плейлиста = " +
                        "${playlistSongs.size} имя первой песни плейлиста " +
                        "= ${playlistSongs.first().title} "
            )

            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,
                    playlist=playlistSongs
                )
            )
            viewModel.setCurrentSong(song)
        }

        // Настраиваем ItemTouchHelper
        val callback=ItemTouchHelperCallback(adapter)
        itemTouchHelper=ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)
        itemTouchHelper.attachToRecyclerView(binding.alltracksPlaylistRecyclerView)

        binding.alltracksPlaylistRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SongPlaylistFragment.adapter
        }

        // Настраиваем поиск
        setupSearch()

        viewModel.loadCurrentPlaylistForSongs(playlistId)  //грузим текущий плейлист ради песен для адаптера

        // Слушаем ОБЩИЙ список песен плейлиста (не отфильтрованный)
        viewModel.currentPlaylistSongs.observe(viewLifecycleOwner) { playlistSongs ->
            totalSongsCount =playlistSongs?.size ?:0
            updateSearchVisibility( )

            Log.d(TAG, "###3 SongPlaylistFragment total songs: $totalSongsCount")
        }

        viewModel.currentFilteredPlaylistSongs.observe(viewLifecycleOwner) { currentPlaylistSongs ->
            Log.d(TAG, "!@#@4 SongPlaylistFragment currentFilteredPlaylistSongs.observe currentFilteredPlaylistSongs size ${currentPlaylistSongs?.size}")
            val currentSongs = currentPlaylistSongs?: listOf()
            //adapter.data = getSortedDataSong(currentSongs) //нельзя, собьётся перемещение строк
            adapter.data = currentSongs

            // Обновляем видимость пустого состояния (только для фильтрации)
            val showEmptyState =currentPlaylistSongs?.isEmpty() != false//пусто после поиска
            val  songCountLessZero = totalSongsCount <= 0  // нет песен в плейлисте

            //tvEmptyPlaylistSong-текст  emptyImageViewPlaylist-картинка
            binding.tvEmptyPlaylistSong.visibility =
                if (showEmptyState || songCountLessZero) View.VISIBLE else View.GONE
            binding.emptyImageViewPlaylist.visibility =
                if (songCountLessZero) View.VISIBLE else View.GONE

            // Обновляем текст пустого состояния
            if (showEmptyState && !songCountLessZero) {
                binding.tvEmptyPlaylistSong.text = "По вашему запросу ничего не найдено"
            } else if (songCountLessZero) {
                binding.tvEmptyPlaylistSong.text = "Здесь пока нет ни одной песни"
            }

            // обновляем меню при загрузке данных
            requireActivity().invalidateOptionsMenu()
        }

        // Также подписываемся на изменения текущего плейлиста
        viewModel.currentPlaylist.observe(viewLifecycleOwner) { playlistWithSongs ->
            playlistWithSongs?.let {
                adapter.data = it.playlistSongs
            }
        }

        initMenu()
    }

    private fun resetEditModeOnStart() {
        // Гарантируем, что при открытии фрагмента режим редактирования выключен
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
        }
    }

    override fun onResume() {
        super.onResume()
        // Также сбрасываем при возобновлении фрагмента
        resetEditModeOnStart()

        // Обновляем меню
        activity?.invalidateOptionsMenu()
    }

    override fun onPause() {
        super.onPause()
        // Сбрасываем режим редактирования при уходе с фрагмента
        resetEditModeOnPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Сбрасываем заголовок при уходе с фрагмента
        (requireActivity() as? MainActivity)?.resetToTabTitle()
        // Сбрасываем режим редактирования при уничтожении вью
        resetEditModeOnPause()
        _binding = null
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    private fun resetEditModeOnPause() {
        // Если был включен режим редактирования, выключаем его
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
            Log.d(TAG, "Режим редактирования сброшен при уходе с фрагмента")
        }
    }

    companion object {
        const val TAG = "33333"
        private const val ARG_ALLTRACKSLIST = "ARG_ALLTRACKSLIST"

        fun newInstance( alltrackslist: List<Song>): SongPlaylistFragment {
            return SongPlaylistFragment().apply {
                arguments = bundleOf(
                    ARG_ALLTRACKSLIST to ArrayList(alltrackslist))
            }
        }
    }

    private fun setupSearch() {
        // Устанавливаем слушатель на иконку поиска
        binding.inputLayoutSearchAllTracksPlaylist.setEndIconOnClickListener {
            performSearch()
        }

        // Устанавливаем слушатель на нажатие Enter на клавиатуре
        binding.inputEditTextSearchAllTracksPlaylist.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }

        // Устанавливаем TextWatcher для поиска при вводе текста
        binding.inputEditTextSearchAllTracksPlaylist.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Не нужно
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Ищем при каждом изменении текста
                s?.let { query ->
                    viewModel.filterPlaylistSongs(query.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotEmpty() == true
                binding.inputLayoutSearchAllTracksPlaylist.endIconMode =
                    if (hasText) TextInputLayout.END_ICON_CLEAR_TEXT
                    else TextInputLayout.END_ICON_CUSTOM
                binding.inputLayoutSearchAllTracksPlaylist.setEndIconDrawable(
                    if (hasText) R.drawable.baseline_close_24
                    else R.drawable.baseline_search_24
                )
            }
        })

        // Очистка по нажатию на иконку
        binding.inputLayoutSearchAllTracksPlaylist.setEndIconOnClickListener {
            if (binding.inputEditTextSearchAllTracksPlaylist.text?.isNotEmpty() == true) {
                binding.inputEditTextSearchAllTracksPlaylist.text?.clear()
                viewModel.filterPlaylistSongs("")
            } else {
                performSearch()
            }
        }

        // Перехватываем нажатие кнопки "Назад" для поля ввода
        setupBackButtonHandler()
    }

    // Перехватываем события клавиатуры для кнопки "Назад"
    private fun setupBackButtonHandler() {
        binding.inputEditTextSearchAllTracksPlaylist.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.inputEditTextSearchAllTracksPlaylist.hasFocus()) {
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
        binding.inputEditTextSearchAllTracksPlaylist.clearFocus()

        // Гарантированно скрываем курсор
        binding.inputEditTextSearchAllTracksPlaylist.isCursorVisible = false

        //стираем текст в поле ввода и показываем весь список
        if (binding.inputEditTextSearchAllTracksPlaylist.text?.isNotEmpty() == true) {
            binding.inputEditTextSearchAllTracksPlaylist.text?.clear()
            viewModel.filterFavoriteSongs("")
        }

        // Скрываем клавиатуру
        hideKeyboard()

        Log.d(TAG, " SongPlaylistFragment hideKeyboardAndClearFocus: фокус снят, курсор скрыт")
    }

    private fun performSearch() {
        val query = binding.inputEditTextSearchAllTracksPlaylist.text.toString().trim()
        if (query.isNotEmpty()) {
            viewModel.filterPlaylistSongs(query)
            hideKeyboard()
        } else {
            toast("Введите текст для поиска")
        }
    }

    private fun updateSearchVisibility() {
        // Показываем строку поиска только если ЕСТЬ песни в избранном
        val shouldShowSearch = totalSongsCount > 0
        Log.d(TAG, " ###1 SongPlaylistFragment updateSearchVisibility:  shouldShowSearch=$shouldShowSearch")
        if (shouldShowSearch) {
            // Показываем строку поиска с анимацией
                Log.d(TAG, " ###1-1 SongPlaylistFragment updateSearchVisibility внутри if-да")
                binding.inputLayoutSearchAllTracksPlaylist.visibility = View.VISIBLE
                binding.inputLayoutSearchAllTracksPlaylist.alpha = 0f
                binding.inputLayoutSearchAllTracksPlaylist.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()

                // Обновляем constraints для RecyclerView
                val params = binding.alltracksPlaylistRecyclerView.layoutParams as ConstraintLayout.LayoutParams
                params.topToTop = ConstraintLayout.LayoutParams.UNSET
                params.topToBottom = binding.inputLayoutSearchAllTracksPlaylist.id
                binding.alltracksPlaylistRecyclerView.layoutParams = params
        } else {
            // Скрываем строку поиска с анимацией
                Log.d(TAG, " ###1-2 SongPlaylistFragment updateSearchVisibility внутри if-нет")
                binding.inputLayoutSearchAllTracksPlaylist.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.inputLayoutSearchAllTracksPlaylist.visibility = View.INVISIBLE
                        // Обновляем constraints для RecyclerView
                        val params = binding.alltracksPlaylistRecyclerView.layoutParams as ConstraintLayout.LayoutParams
                        params.topToBottom = ConstraintLayout.LayoutParams.UNSET
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        binding.alltracksPlaylistRecyclerView.layoutParams = params
                    }
                    .start()

                // Очищаем текст поиска
                binding.inputEditTextSearchAllTracksPlaylist.text?.clear()
                viewModel.filterPlaylistSongs("")
                hideKeyboard()
        }

        Log.d(TAG, " ###2 SongPlaylistFragment updateSearchVisibility: totalSongsCount=$totalSongsCount, shouldShowSearch=$shouldShowSearch")
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputEditTextSearchAllTracksPlaylist.windowToken, 0)
    }

    fun initMenu() {
        Log.d(TAG, "$$$$$ SongPlaylistFragment initMenu")
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_other_2, menu)

                // Показываем/скрываем пункт в зависимости от режима
                val editItem = menu.findItem(R.id.action_edit_order2)
                editItem.title = if (isEditMode) "Готово" else "Редактировать порядок"
            }

            override fun onPrepareMenu(menu: Menu) {
                //menu.findItem(R.id.action_edit_order2).isVisible =false
                val editItem = menu.findItem(R.id.action_edit_order2)

                // Меняем цвет в зависимости от режима
                val color = if (isEditMode) {
                    ContextCompat.getColor(requireContext(), R.color.green)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.white)
                }
                editItem?.icon?.setTint(color)

                // Обновляем текст кнопки
                editItem?.title = if (isEditMode) "Готово" else "Редактировать порядок"

                // вычисляем количество песен в списке
                val songsCount = viewModel.currentFilteredPlaylistSongs.value?.size ?: 0
               Log.d(TAG, "$$$$$5 SongPlaylistFragment onPrepareMenu songsCount = $songsCount ")
                // Простое условие - больше 7 песен
                menu.findItem(R.id.action_go_to_song2).isVisible = songsCount > 7
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when(menuItem.itemId){
                    R.id.action_go_to_song2->{
                        //список песен в плейлисте
                        val currentPlaylistSongs  = viewModel.getCurrentPlaylist()?.playlistSongs ?: listOf()
                       //текущая песня
                        val currentSong = viewModel.getCurrentSong()

                        val currentSongMediaUri = getNormalizedPath(currentSong?.mediaUri ?: "")
                        val indexOfSong = currentPlaylistSongs.indexOfFirst { getNormalizedPath(it.mediaUri) == currentSongMediaUri }
                        Log.d(TAG, "4$$$ SongPlaylistFragment onMenuItemSelected indexOfSong = $indexOfSong currentSongMediaUri = $currentSongMediaUri")

                        (binding.alltracksPlaylistRecyclerView.layoutManager as LinearLayoutManager).let{
                            if(indexOfSong >= 0 ) it.scrollToPositionWithOffset(indexOfSong, 0) else it.scrollToPosition(0)
                        }
                        return true
                    }
                    R.id.action_edit_order2 -> {
                        toggleEditMode()
                        true
                    }
                    R.id.action_shuffle->{
                        val newShuffleState = playerViewModel.setPlaybackMode()
                        // Меняем иконку в зависимости от состояния
                        if (newShuffleState) {
                            menuItem.setIcon(R.drawable.baseline_shuffle_24) // подсвеченная иконка
                            Toast.makeText(context, "Случайный порядок включен", Toast.LENGTH_SHORT).show()
                        } else {
                            menuItem.setIcon(R.drawable.icons8_data_white_24) // обычная иконка
                            Toast.makeText(context, "Прямой порядок включен", Toast.LENGTH_SHORT).show()
                        }
                        return   true
                    }
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun toggleEditMode() {
        val newEditMode = !isEditMode
        isEditMode = newEditMode
        adapter.setEditMode(newEditMode)

        // Включаем/выключаем возможность перетаскивания
        if (isEditMode) {
            itemTouchHelper.attachToRecyclerView(binding.alltracksPlaylistRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
        }

        // Обновляем меню
        activity?.invalidateOptionsMenu()
        Log.d(TAG, "Режим редактирования: $newEditMode")
    }
    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте песни для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

}