package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.accentColor
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentPlaylistBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.adapters.PlaylistAdapter
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperCallback
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class PlaylistFragment():Fragment() {
    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private lateinit var adapter: PlaylistAdapter
    private var isEditMode = false
    private lateinit var itemTouchHelper: ItemTouchHelper
    // Флаг, чтобы различать состояния "список пуст изначально" и "ничего не найдено"
    private var isSearchActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //viewModel.setAppBarTitle("Плейлисты") //сбивает правильные заголовки для TabFragment

        // Изначально показываем картинку-заглушку и крутилку
        showLoadingState()

        adapter = PlaylistAdapter (viewModel, playlistsViewModel){ playlist ->
            //здесь setPlaylist и setSongAndPlaylist не делаем, так как здесь нет песен

            // Навигация через Bundle
            val bundle = Bundle().apply {
                putLong("playlistId", playlist.id)
                putString("playlistName", playlist.playlistName)
                Log.d(TAG,"33 PlaylistFragment onViewCreated bundle: playlist = ${playlist.id} " +
                        " playlistName = ${playlist.playlistName} from = 5 ")
            }
            findNavController().navigate( R.id.songPlaylistFragment, bundle)
        }

        // Настраиваем ItemTouchHelper
        val callback = ItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        // Передаем ItemTouchHelper в адаптер
        adapter.setItemTouchHelper(itemTouchHelper)

        // Изначально НЕ прикрепляем - прикрепим только в режиме редактирования в toggleEditMode
        // itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)

        binding.playlistRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlaylistFragment.adapter
        }

        binding.fabNewPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        //подсветка плейлистов, в которых лежит играющий трек: пересчитываем при смене трека и при изменении плейлистов
        viewModel.currentSong.observe(viewLifecycleOwner) { song ->
            playlistsViewModel.refreshPlaylistsWithSong(song?.id)
        }
        playlistsViewModel.playlists.observe(viewLifecycleOwner) {
            playlistsViewModel.refreshPlaylistsWithSong(viewModel.currentSong.value?.id)
        }

        playlistsViewModel.filteredPlaylists.observe(viewLifecycleOwner) { filteredPlaylists ->
            Log.d(TAG,"53 PlaylistFragment onViewCreated filteredPlaylists.observe: filteredPlaylists.size= ${filteredPlaylists.size} ")
            //здесь нельзя делать сортировку, иначе собьётся перемещение папок
            adapter.playlist = filteredPlaylists  //передаём данные в адаптер

            // Управляем видимостью в зависимости от состояния
            updateEmptyState(filteredPlaylists)
        }
    }

    /**
     * Показать состояние загрузки: картинка + прогрессбар, текст скрыт.
     * Вызывается при открытии фрагмента.
     */
    private fun showLoadingState() {
        binding.emptyImageViewPlaylists.visibility = View.VISIBLE
        binding.progressBarPlaylists.visibility = View.VISIBLE
        binding.tvEmptyPlaylists.visibility = View.GONE
    }

    /**
     * Обновить состояние пустого списка.
     * @param playlists текущий список (после фильтрации).
     */
    private fun updateEmptyState(playlists: List<Playlist>) {
        if (playlists.isEmpty()) {
            // Список пуст — скрываем прогрессбар, показываем картинку и текст
            binding.progressBarPlaylists.visibility = View.GONE
            binding.emptyImageViewPlaylists.visibility = View.VISIBLE
            binding.tvEmptyPlaylists.visibility = View.VISIBLE

            // Выбираем текст в зависимости от того, активен ли поиск
            binding.tvEmptyPlaylists.text = if (isSearchActive) {
                "По вашему запросу ничего не найдено"
            } else {
                "У вас пока нет плейлистов"
            }
        } else {
            // Список не пуст — всё скрываем
            binding.progressBarPlaylists.visibility = View.GONE
            binding.emptyImageViewPlaylists.visibility = View.GONE
            binding.tvEmptyPlaylists.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Сбрасываем режим редактирования при возвращении
        resetEditModeOnStart()
        // Обновляем меню
        requireActivity().invalidateOptionsMenu()
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()

        // Сбрасываем режим редактирования при уходе
        resetEditModeOnPause()

        //определяем первую видимую позицию
        val manager = binding.playlistRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        Log.d(TAG, "PlaylistFragment onPause firstPosition = $firstPosition")
        playlistsViewModel.savePositionPlaylist(firstPosition)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): PlaylistFragment {
            this.viewPager = viewPager
            return PlaylistFragment()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_other, menu)

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar_other)
        val searchView =searchItem.actionView as SearchView
        //значок лупы слева в развёрнутом сост и сворачиваем строку поиска (true)
        searchView.setIconifiedByDefault(true)
        //пишем подсказку в строке поиска
        searchView.queryHint = getString(R.string.search_playlist)
        //устанавливаем в панели действий кнопку ( > )для отправки поискового запроса
        searchView.isSubmitButtonEnabled = true
        //устанавливаем слушатель
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                // Устанавливаем флаг, что поиск активен, если есть текст
                isSearchActive = !newText.isNullOrEmpty()
                playlistsViewModel.filterPlaylists(newText.orEmpty())
                return true
            }
        })

        // Сбрасываем флаг поиска при закрытии SearchView
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchActive = false
                playlistsViewModel.filterPlaylists("") // сброс фильтра
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_go_to_song).isVisible =false

        // Настраиваем кнопку редактирования
        val editItem = menu.findItem(R.id.action_edit_order)
        // Меняем текст
        editItem.title = if (isEditMode) "Готово" else "Редактировать порядок"

        // Меняем цвет в зависимости от режима
        val color = if (isEditMode) {
            requireContext().accentColor()
        } else {
            ContextCompat.getColor(requireContext(), R.color.white)
        }
        editItem?.icon?.setTint(color)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_edit_order -> {
                toggleEditMode()
                true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun resetEditModeOnStart() {
        // Гарантируем, что при открытии фрагмента режим редактирования выключен
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
        }
    }

    private fun resetEditModeOnPause() {
        // Сбрасываем режим редактирования при уходе с фрагмента
        if (isEditMode) {
            isEditMode = false
            adapter.setEditMode(false)
            itemTouchHelper.attachToRecyclerView(null)
            Log.d(TAG, "PlaylistFragment: Режим редактирования сброшен при уходе")
        }
    }

    private fun toggleEditMode() {

        isEditMode = !isEditMode
        adapter.setEditMode(isEditMode)

        // Включаем/выключаем возможность перетаскивания
        if (isEditMode) {
            itemTouchHelper.attachToRecyclerView(binding.playlistRecyclerView)
            showEditModeHint()
        } else {
            itemTouchHelper.attachToRecyclerView(null) // Отключаем перетаскивание
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

    private fun showCreatePlaylistDialog() {
        val dialog = Dialog(requireContext()).apply {
            setContentView(R.layout.dialog_create_playlist)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        // Находим все элементы
        val editText = dialog.findViewById<TextInputEditText>(R.id.etPlaylistName)
        val charCount = dialog.findViewById<TextView>(R.id.tvCharCount) // Вот эта переменная!
        val btnCreate = dialog.findViewById<Button>(R.id.btnCreate)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

        // Счетчик символов
        editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                charCount?.text = "${s?.length ?: 0}/50"
            }
        })

        btnCreate.setOnClickListener {
            val name=editText.text.toString().trim()
            if (name.isNotEmpty()) {
                playlistsViewModel.createPlaylist(name){
                    // После создания плейлиста состояние обновится автоматически
                    // через observer filteredPlaylists
                    //binding.emptyImageViewPlaylists.visibility =  View.GONE
                    //binding.tvEmptyPlaylists.visibility = View.GONE
                }
                dialog.dismiss()
            } else {
                editText.error="Введите название плейлиста"
            }
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        // Показать клавиатуру при открытии
        editText?.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.show()
    }

}