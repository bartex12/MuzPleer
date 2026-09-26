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
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentAlltracksBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.SongsAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataSong
import com.example.muzpleer.util.toast
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SongListAlbumFragment:Fragment() {
    private var _binding: FragmentAlltracksBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongsAdapter
    private val viewModel: SharedViewModel by activityViewModel()
    private var totalSongsCount = 0  // Храним общее количество песен (не отфильтрованных)
    private var albumName: String? = null //Название альбома
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumName = arguments?.getString("albumName")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlltracksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //viewModel.setAppBarTitle("Альбом: $albumName") //можно и так
        // Устанавливаем заголовок
        updateToolbarTitle("Альбом: $albumName")
        navController = findNavController()

        initMenu()

        adapter = SongsAdapter(viewModel, navController) { song ->
            val playlist=viewModel.getPlaylist()

            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,
                    playlist=playlist
                )
            )
            viewModel.setCurrentSong(song)
        }

        binding.alltracksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SongListAlbumFragment.adapter
        }

        // Настраиваем поиск
        setupSearch()

        if (arguments?.getLong("albumId") != null)  {
            val albumId = arguments?.getLong("albumId")!!
            Log.d(TAG, "40!@# SongListAlbumFragment arguments albumId  $albumId")

            viewModel.getSongsByAlbum(albumId)

            // Слушаем ОБЩИЙ список  песен (не отфильтрованный)
            viewModel.listAlbumSong.observe(viewLifecycleOwner) { albumSongs ->
                totalSongsCount = albumSongs.size
                updateSearchVisibility()
                Log.d(TAG, "###SongListAlbumFragment totalSongsCount: $totalSongsCount")
            }

            viewModel.filteredListAlbumSong.observe(viewLifecycleOwner) { albumSongs ->
                Log.d(TAG, "41!@# SongListAlbumFragment arguments filteredListAlbumSong size ${albumSongs.size}")
                //здесь нельзя делать сортировку, иначе собьётся перемещение !!!
                //adapter.data = getSortedDataSong(albumSongs)
                adapter.data = albumSongs

                // Обновляем видимость пустого состояния (только для фильтрации)
                val showEmptyState = albumSongs.isEmpty() //пусто после поиска
                val  songCountLessZero = totalSongsCount <= 0  // нет песен в избранном

                //favoriteEmpty-текст  emptyImageViewFavorite-картинка
                binding.favoriteEmpty.visibility =
                    if (showEmptyState || songCountLessZero) View.VISIBLE else View.GONE
                binding.emptyImageViewFavoriteAllTrack.visibility =
                    if (songCountLessZero) View.VISIBLE else View.GONE

                // Обновляем текст пустого состояния
                if (showEmptyState && !songCountLessZero) {
                    binding.favoriteEmpty.text = "По вашему запросу ничего не найдено"
                } else if (songCountLessZero) {
                    binding.favoriteEmpty.text = "Здесь пока нет ни одной песни"
                }

                // обновляем меню при загрузке данных
                requireActivity().invalidateOptionsMenu()
            }
            //обновление обложки при её замене
            viewModel.coverImageUri.observe(viewLifecycleOwner) { uri ->
                val selectedSong = viewModel.getSelectedSong()
                selectedSong?. let{selectedSong->
                    selectedSong.artUri = uri.toString()
                    Log.d(TAG,"42!@# SongListAlbumFragment coverImageUri.observe uri = $uri ")
                }
                adapter.notifyDataSetChanged()
            }
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

    companion object {
        const val TAG = "33333"
        private const val ARG_ALLTRACKSLIST = "ARG_ALLTRACKSLIST"

        fun newInstance( alltrackslist: List<Song>): SongListAlbumFragment {
            return SongListAlbumFragment().apply {
                arguments = bundleOf(
                    ARG_ALLTRACKSLIST to ArrayList(alltrackslist))
            }
        }
    }

    private fun setupSearch() {
        // Устанавливаем слушатель на иконку поиска
        binding.inputLayoutSearchAllTracks.setEndIconOnClickListener {
            performSearch()
        }

        // Устанавливаем слушатель на нажатие Enter на клавиатуре
        binding.inputEditTextSearchAllTracks.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }

        // Устанавливаем TextWatcher для поиска при вводе текста
        binding.inputEditTextSearchAllTracks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Не нужно
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Ищем при каждом изменении текста
                s?.let { query ->
                    viewModel.filterAlbumSongs(query.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotEmpty() == true
                binding.inputLayoutSearchAllTracks.endIconMode =
                    if (hasText) TextInputLayout.END_ICON_CLEAR_TEXT
                    else TextInputLayout.END_ICON_CUSTOM
                binding.inputLayoutSearchAllTracks.setEndIconDrawable(
                    if (hasText) R.drawable.baseline_close_24
                    else R.drawable.baseline_search_24
                )
            }
        })

        // Очистка по нажатию на иконку
        binding.inputLayoutSearchAllTracks.setEndIconOnClickListener {
            if (binding.inputEditTextSearchAllTracks.text?.isNotEmpty() == true) {
                binding.inputEditTextSearchAllTracks.text?.clear()
                viewModel.filterAlbumSongs("")
            } else {
                performSearch()
            }
        }

        // Перехватываем нажатие кнопки "Назад" для поля ввода
        setupBackButtonHandler()
    }

    // Перехватываем события клавиатуры для кнопки "Назад"
    private fun setupBackButtonHandler() {
        binding.inputEditTextSearchAllTracks.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.inputEditTextSearchAllTracks.hasFocus()) {
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
        binding.inputEditTextSearchAllTracks.clearFocus()

        // Гарантированно скрываем курсор
        binding.inputEditTextSearchAllTracks.isCursorVisible = false

        //стираем текст в поле ввода и показываем весь список
        if (binding.inputEditTextSearchAllTracks.text?.isNotEmpty() == true) {
            binding.inputEditTextSearchAllTracks.text?.clear()
            viewModel.filterAlbumSongs("")
        }

        // Скрываем клавиатуру
        hideKeyboard()

        Log.d(TAG, "SongListAlbumFragment hideKeyboardAndClearFocus: фокус снят, курсор скрыт")
    }

    private fun performSearch() {
        val query = binding.inputEditTextSearchAllTracks.text.toString().trim()
        if (query.isNotEmpty()) {
            viewModel.filterAlbumSongs(query)
            hideKeyboard()
        } else {
            toast("Введите текст для поиска")
        }
    }

    private fun updateSearchVisibility() {
        // Показываем строку поиска только если ЕСТЬ песни в списке
        val shouldShowSearch = totalSongsCount > 0

        if (shouldShowSearch) {
            // Показываем строку поиска с анимацией
            if (binding.inputLayoutSearchAllTracks.visibility != View.VISIBLE) {
                binding.inputLayoutSearchAllTracks.visibility = View.VISIBLE
                binding.inputLayoutSearchAllTracks.alpha = 0f
                binding.inputLayoutSearchAllTracks.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()

                // Обновляем constraints для RecyclerView
                val params = binding.alltracksRecyclerView.layoutParams as ConstraintLayout.LayoutParams
                params.topToTop = ConstraintLayout.LayoutParams.UNSET
                params.topToBottom = binding.inputLayoutSearchAllTracks.id
                binding.alltracksRecyclerView.layoutParams = params
            }
        } else {
            // Скрываем строку поиска с анимацией
            if (binding.inputLayoutSearchAllTracks.visibility != View.GONE) {
                binding.inputLayoutSearchAllTracks.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.inputLayoutSearchAllTracks.visibility = View.GONE
                        // Обновляем constraints для RecyclerView
                        val params = binding.alltracksRecyclerView.layoutParams as ConstraintLayout.LayoutParams
                        params.topToBottom = ConstraintLayout.LayoutParams.UNSET
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        binding.alltracksRecyclerView.layoutParams = params
                    }
                    .start()

                // Очищаем текст поиска
                binding.inputEditTextSearchAllTracks.text?.clear()
                viewModel.filterAlbumSongs("")
                hideKeyboard()
            }
        }

        Log.d(TAG, "SongListAlbumFragment updateSearchVisibility: totalSongsCount=$totalSongsCount, shouldShowSearch=$shouldShowSearch")
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputEditTextSearchAllTracks.windowToken, 0)
    }


    fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_other_2, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_edit_order2).isVisible = false
                var songsCount = 0
                songsCount =  viewModel.filteredListAlbumSong.value?.size?:0
                Log.d(TAG, "$$$$$ SongListAlbumFragment onPrepareMenu songsCount = $songsCount ")
                // Простое условие - больше 7 песен
                menu.findItem(R.id.action_go_to_song2).isVisible = songsCount > 7
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when(menuItem.itemId){
                    R.id.action_go_to_song2->{
                        val albumSongs = viewModel.listAlbumSong.value //список песен альюома
                        val currentSong = viewModel.getCurrentSong()
                        if (albumSongs!=null){
                            val indexOfSong = albumSongs.indexOfFirst { it.mediaUri == currentSong?.mediaUri }
                            Log.d(TAG, "1$$$ SongListAlbumFragment onMenuItemSelected indexOfSong = $indexOfSong")
                            (binding.alltracksRecyclerView.layoutManager as LinearLayoutManager).let{
                                if(indexOfSong >= 0 ) it.scrollToPositionWithOffset(indexOfSong, 0) else it.scrollToPosition(0)
                            }
                        }
                        return true
                    }
                    R.id.action_shuffle->{
                        val newShuffleState = viewModel.setPlaybackMode()
                        // Меняем иконку в зависимости от состояния
                        if (newShuffleState) {
                            menuItem.setIcon(R.drawable.icons8_shuffle_white_24) // подсвеченная иконка
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

}