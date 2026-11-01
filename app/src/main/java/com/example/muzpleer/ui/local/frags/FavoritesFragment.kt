package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.indexOfSong
import com.example.muzpleer.util.applyShuffleIcon
import com.example.muzpleer.util.accentColor
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
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentFavoriteBinding
import com.example.muzpleer.model.PlaybackSource
import com.example.muzpleer.model.SourceType
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.FavoritesAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.player.PlayerViewModel
import com.example.muzpleer.util.syncShuffleIcon
import com.example.muzpleer.util.toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class FavoritesFragment: Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private lateinit var adapter: FavoritesAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false
    private var totalSongsCount = 0  // Храним общее количество песен (не отфильтрованных)
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сброс режима редактирования при каждом открытии фрагмента
        resetEditModeOnStart()
        navController = findNavController()

        adapter = FavoritesAdapter(viewModel, playlistsViewModel, favoritesViewModel, navController) { song ->
            //устанавливаем список песен как плейлист
            val playlist=favoritesViewModel.getFavoriteSongs()
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист
            viewModel.setPlaybackSource(PlaybackSource(SourceType.FAVORITES))
            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,  //текущая песня
                    playlist=playlist //текущий плейлист
                )
            )
        }

        // Настраиваем ItemTouchHelper
        val callback=ItemTouchHelperCallback(adapter)
        itemTouchHelper=ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)
        itemTouchHelper.attachToRecyclerView(binding.favoriteRecyclerView)

        binding.favoriteRecyclerView.apply {
            layoutManager=LinearLayoutManager(requireContext())
            adapter=this@FavoritesFragment.adapter
        }

        // Настраиваем поиск
        setupSearch()

        //загружаем избранные песни
        favoritesViewModel.loadFavoriteSongs()

        // Слушаем ОБЩИЙ список избранных песен (не отфильтрованный)
        favoritesViewModel.favoriteSongs.observe(viewLifecycleOwner) { allFavorites ->
            totalSongsCount = allFavorites.size
            updateSearchVisibility()

            Log.d(TAG, "###FavoritesFragment total songs: $totalSongsCount")
        }

        favoritesViewModel.filteredFavoriteSongs.observe(viewLifecycleOwner) { filteredFavorites ->
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок!!!
            //val sortedData = getSortedDataSong(filteredFavorites)
            // adapter.data = sortedData  //передаём данные в адаптер
            adapter.data = filteredFavorites  //передаём данные в адаптер

            // Обновляем видимость пустого состояния (только для фильтрации)
            val showEmptyState = filteredFavorites.isEmpty() //пусто после поиска
            val  songCountLessZero = totalSongsCount <= 0  // нет песен в избранном

            //favoriteEmpty-текст  emptyImageViewFavorite-картинка
            binding.favoriteEmpty.visibility =
                if (showEmptyState || songCountLessZero) View.VISIBLE else View.GONE
            binding.emptyImageViewFavorite.visibility =
                if (songCountLessZero) View.VISIBLE else View.GONE

            // Обновляем текст пустого состояния
            if (showEmptyState && !songCountLessZero) {
                binding.favoriteEmpty.text = "По вашему запросу ничего не найдено"
            } else if (songCountLessZero) {
                binding.favoriteEmpty.text = "Здесь пока нет ни одного трека"
            }

            // обновляем меню при загрузке данных
            requireActivity().invalidateOptionsMenu()
        }

        //восстанавливаем позицию списка после поворота или возвращения на экран и при новой загрузке
        binding.favoriteRecyclerView.layoutManager?.scrollToPosition(favoritesViewModel.getPositionFavoriteSong())

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

    private fun setupSearch() {
        // Устанавливаем слушатель на иконку поиска
        binding.inputLayoutSearch.setEndIconOnClickListener {
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
                    favoritesViewModel.filterFavoriteSongs(query.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotEmpty() == true
                binding.inputLayoutSearch.endIconMode =
                    if (hasText) TextInputLayout.END_ICON_CLEAR_TEXT
                    else TextInputLayout.END_ICON_CUSTOM
                binding.inputLayoutSearch.setEndIconDrawable(
                    if (hasText) R.drawable.baseline_close_24
                    else R.drawable.baseline_search_24
                )
            }
        })

        // Очистка по нажатию на иконку
        binding.inputLayoutSearch.setEndIconOnClickListener {
            if (binding.inputEditTextSearch.text?.isNotEmpty() == true) {
                binding.inputEditTextSearch.text?.clear()
                favoritesViewModel.filterFavoriteSongs("")
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
            favoritesViewModel.filterFavoriteSongs("")
        }

        // Скрываем клавиатуру
        hideKeyboard()

        Log.d(TAG, "hideKeyboardAndClearFocus: фокус снят, курсор скрыт")
    }

    private fun performSearch() {
        val query = binding.inputEditTextSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            favoritesViewModel.filterFavoriteSongs(query)
            hideKeyboard()
        } else {
            toast("Введите текст для поиска")
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputEditTextSearch.windowToken, 0)
    }

    private fun updateSearchVisibility() {
        // Показываем строку поиска только если ЕСТЬ песни в избранном
        val shouldShowSearch = totalSongsCount > 0

        if (shouldShowSearch) {
            // Показываем строку поиска с анимацией
            if (binding.inputLayoutSearch.visibility != View.VISIBLE) {
                binding.inputLayoutSearch.visibility = View.VISIBLE
                binding.inputLayoutSearch.alpha = 0f
                binding.inputLayoutSearch.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()

                // Обновляем constraints для RecyclerView
                val params = binding.favoriteRecyclerView.layoutParams as ConstraintLayout.LayoutParams
                params.topToTop = ConstraintLayout.LayoutParams.UNSET
                params.topToBottom = binding.inputLayoutSearch.id
                binding.favoriteRecyclerView.layoutParams = params
            }
        } else {
            // Скрываем строку поиска с анимацией
            if (binding.inputLayoutSearch.visibility != View.GONE) {
                binding.inputLayoutSearch.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.inputLayoutSearch.visibility = View.GONE
                        // Обновляем constraints для RecyclerView
                        val params = binding.favoriteRecyclerView.layoutParams as ConstraintLayout.LayoutParams
                        params.topToBottom = ConstraintLayout.LayoutParams.UNSET
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        binding.favoriteRecyclerView.layoutParams = params
                    }
                    .start()

                // Очищаем текст поиска
                binding.inputEditTextSearch.text?.clear()
                favoritesViewModel.filterFavoriteSongs("")
                hideKeyboard()
            }
        }

        Log.d(TAG, "updateSearchVisibility: totalSongsCount=$totalSongsCount, shouldShowSearch=$shouldShowSearch")
    }

    override fun onResume() {
        super.onResume()
        // Также сбрасываем при возобновлении фрагмента
        resetEditModeOnStart()

        // Обновляем меню
        activity?.invalidateOptionsMenu()
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()

        // Сбрасываем режим редактирования при уходе с фрагмента
        resetEditModeOnPause()

        //определяем первую видимую позицию
        val manager = binding.favoriteRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        favoritesViewModel.savePositionFavoriteSong(firstPosition)
        Log.d(TAG, "SongFragment onPause firstPosition = $firstPosition")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Сбрасываем режим редактирования при уничтожении вью
        resetEditModeOnPause()
        _binding = null
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
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): FavoritesFragment {
            this.viewPager = viewPager
            return FavoritesFragment()
        }
    }

    fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_favorites, menu)
                menu.syncShuffleIcon(requireContext(), playerViewModel.isShuffleMode())

                // Показываем/скрываем пункт в зависимости от режима
                val editItem = menu.findItem(R.id.action_edit_order_favorite)
                editItem?.title = if (isEditMode) "Редактирование порядка" else "Редактировать порядок"
            }
            override fun onPrepareMenu(menu: Menu) {
                menu.syncShuffleIcon(requireContext(), playerViewModel.isShuffleMode())
                super.onPrepareMenu(menu)
                val editItem = menu.findItem(R.id.action_edit_order_favorite)

                // Меняем цвет в зависимости от режима
                val color = if (isEditMode) {
                    requireContext().accentColor()
                } else {
                    ContextCompat.getColor(requireContext(), R.color.white)
                }
                editItem?.icon?.setTint(color)

                // Обновляем текст кнопки
                editItem?.title = if (isEditMode) "Редактирование порядка" else "Редактировать порядок"

                // вычисляем количество песен в списке
                val songsCount = favoritesViewModel.filteredFavoriteSongs.value?.size ?: 0
                Log.d(TAG, "$$$$$ SongPlaylistFragment onPrepareMenu songsCount = $songsCount ")
                // Простое условие - больше  7 песен
                menu.findItem(R.id.action_go_to_song_favorite)?.isVisible = songsCount > 7
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when(menuItem.itemId){
                    R.id.action_go_to_song_favorite->{
                        val favoriteSongs = favoritesViewModel.getFavoriteSongs() //список песен артиста
                        val currentSong = viewModel.getCurrentSong()
                        val indexOfSong = favoriteSongs.indexOfSong(currentSong)
                        Log.d(TAG, "$$$ FavoritesFragment onMenuItemSelected indexOfSong = $indexOfSong")
                        (binding.favoriteRecyclerView.layoutManager as LinearLayoutManager).let{
                            if(indexOfSong >= 0 ) it.scrollToPositionWithOffset(indexOfSong, 0) else { it.scrollToPosition(0); android.widget.Toast.makeText(requireContext(), "Играющий трек в этом списке не найден", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                        return true
                    }

                    R.id.action_edit_order_favorite -> {
                        toggleEditMode()
                        true
                    }

                    R.id.action_shuffle->{
                        val newShuffleState = playerViewModel.setPlaybackMode()
                        // Меняем иконку в зависимости от состояния
                        if (newShuffleState) {
                            menuItem.applyShuffleIcon(requireContext(), true)
                            Toast.makeText(context, "Случайный порядок включен", Toast.LENGTH_SHORT).show()
                        } else {
                            menuItem.applyShuffleIcon(requireContext(), false)
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
            itemTouchHelper.attachToRecyclerView(binding.favoriteRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
           // saveOrderChanges()
        }
        // Обновляем меню
        activity?.invalidateOptionsMenu()
    }

    private fun showEditModeHint() {
        Snackbar.make(binding.root, "Перетаскивайте треки для изменения порядка",
            Snackbar.LENGTH_LONG)
            .setAction("OK") {toggleEditMode() }
            .show()
    }

}