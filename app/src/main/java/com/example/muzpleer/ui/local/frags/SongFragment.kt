package com.example.muzpleer.ui.local.frags

import com.example.muzpleer.util.applyShuffleIcon
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentSongsBinding
import com.example.muzpleer.di.App
import com.example.muzpleer.model.PlaybackSource
import com.example.muzpleer.model.SourceType
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.model.Song
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.SongsAdapter
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.ui.local.helper.PreferenceHelperImpl
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.AlbumsViewModel
import com.example.muzpleer.ui.local.viewmodel.ArtistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.player.PlayerViewModel
import com.example.muzpleer.util.getSortedDataSong
import com.example.muzpleer.util.syncShuffleIcon
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SongFragment : Fragment() {
    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val playerViewModel: PlayerViewModel by activityViewModel()
    private val albumsViewModel: AlbumsViewModel by activityViewModel()
    private val artistsViewModel: ArtistsViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private lateinit var adapter: SongsAdapter
    private var currentSearchQuery = ""
    private lateinit var appPreferences: IPreferenceHelper
    private lateinit var navController: NavController
    // Флаг, чтобы различать состояния "список пуст изначально" и "ничего не найдено"
    private var isSearchActive = false
    private var searchMenuItem: MenuItem? = null //пункт поиска, чтобы закрыть его при уходе с экрана

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Изначально показываем картинку-заглушку и крутилку
        showLoadingState()

        appPreferences =PreferenceHelperImpl(App.instance)
        navController = findNavController()

        adapter = SongsAdapter(viewModel, playlistsViewModel, favoritesViewModel, navController) { song ->
            //устанавливаем список песен как плейлист
            val playlist=getSortedDataSong(viewModel.getSongs())
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист
            viewModel.setPlaybackSource(PlaybackSource(SourceType.SONGS))
            viewModel.setSongAndPlaylist(
                SongAndPlaylist(
                    song=song,  //текущая песня
                    playlist=playlist //текущий плейлист
                )
            )
        }

        binding.localRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SongFragment.adapter
        }

        viewModel.filteredSongs.observe(viewLifecycleOwner) { filteredSongs ->
            Log.d( TAG,"32 SongsFragment onViewCreated filteredSongs.observe: filteredSongs.size= ${filteredSongs.size} ")
            val sortedData = getSortedDataSong(filteredSongs) //здесь песни отсортированы
            adapter.data = sortedData  //передаём данные в адаптер

            // Управляем видимостью в зависимости от состояния
            if (filteredSongs.isEmpty() && viewModel.libraryLoaded.value != true) {
                // библиотека ещё сканируется: показываем загрузку, а не "У вас пока нет треков"
                showLoadingState()
            } else {
                updateEmptyState(filteredSongs)
            }
        }

        //обновление обложки при её замене
        viewModel.coverImageUri.observe(viewLifecycleOwner) { uri ->
            val selectedSong = viewModel.getSelectedSong()
            selectedSong?. let{selectedSong->
               selectedSong.artUri = uri.toString()
                Log.d( TAG,"6*** SongsFragment coverImageUri.observe uri = $uri ")
           }
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Показать состояние загрузки: картинка + прогрессбар, текст скрыт.
     * Вызывается при открытии фрагмента.
     */
    private fun showLoadingState() {
        binding.imageHolder3.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
    }

    /**
     * Обновить состояние пустого списка.
     * @param playlists текущий список (после фильтрации).
     */
    private fun updateEmptyState(songs: List<Song>) {
        if (songs.isEmpty()) {
            // Список пуст — скрываем прогрессбар, показываем картинку и текст
            binding.progressBar.visibility = View.GONE
            binding.imageHolder3.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.VISIBLE

            // Выбираем текст в зависимости от того, активен ли поиск
            binding.tvEmpty.text = if (isSearchActive) {
                "По вашему запросу ничего не найдено"
            } else {
                "У вас пока нет музыкальных треков"
            }
        } else {
            // Список не пуст — всё скрываем
            binding.progressBar.visibility = View.GONE
            binding.imageHolder3.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val pos = viewModel.getPositionSong()
        Log.d( TAG,"1---SongsFragment onResume PositionSong = $pos ")
        //восстанавливаем позицию списка после поворота или возвращения на экран и при новой загрузке
        val manager = binding.localRecyclerView.layoutManager as LinearLayoutManager
        Log.d( TAG,"1---SongsFragment onResume manager = $manager ")
        manager.scrollToPositionWithOffset(pos, 0)
        // Пересоздаём меню, как на вкладках альбомов, папок и исполнителей
        requireActivity().invalidateOptionsMenu()
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()
        //при уходе с экрана поиск закрывается, как на вкладках альбомов, папок и исполнителей:
        //иначе при возврате список остаётся урезанным, а строка поиска свёрнута и пуста.
        //К этому моменту строка поиска уже свёрнута, поэтому проверять её состояние нельзя -
        //закрываем всегда
        view?.let { v ->
            //прячем клавиатуру
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(v.windowToken, 0)
        }
        //сворачиваем строку поиска (слушатель сворачивания сбросит фильтр)
        searchMenuItem?.collapseActionView()
        isSearchActive = false
        viewModel.filterSongs("")
        Log.d(TAG, "SongFragment onPause поиск сброшен")

        //определяем первую видимую позицию
        val manager = binding.localRecyclerView.layoutManager as LinearLayoutManager
        val firstPosition = manager.findFirstVisibleItemPosition()
        viewModel.savePositionSong(firstPosition)
        Log.d(TAG, "SongFragment onPause firstPosition = $firstPosition")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "33333"
        private lateinit var viewPager: ViewPager

        fun newInstance(viewPager: ViewPager): SongFragment {
            this.viewPager = viewPager
            return SongFragment()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear() // Очищаем меню
        inflater.inflate(R.menu.song_menu, menu)
        menu.syncShuffleIcon(requireContext(), playerViewModel.isShuffleMode())

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar)
        searchMenuItem = searchItem
        val searchView =searchItem.actionView as SearchView
        //значок лупы слева в развёрнутом сост и сворачиваем строку поиска (true)
        searchView.setIconifiedByDefault(true)
        //пишем подсказку в строке поиска
        searchView.queryHint = getString(R.string.search_song)
        //устанавливаем в панели действий кнопку ( > )для отправки поискового запроса
        // searchView.isSubmitButtonEnabled = true

        //Сохраняем состояние поиска при смене ориентации:
        if ( currentSearchQuery.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(currentSearchQuery, false)
        }
        //устанавливаем слушатель
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                // Устанавливаем флаг, что поиск активен, если есть текст
                isSearchActive = !newText.isNullOrEmpty()
                viewModel.filterSongs(newText.orEmpty())
                return true
            }
        })
        // Сбрасываем флаг поиска при закрытии SearchView
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchActive = false
                viewModel.filterSongs("") // сброс фильтра
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.syncShuffleIcon(requireContext(), playerViewModel.isShuffleMode())

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_to->{
                Log.d(TAG, "$ SongFragment onMenuItemSelected текущая вкладка = ${viewPager.currentItem}")
                viewPager.setCurrentItem(0)
                Log.d(TAG, "$$ SongFragment onMenuItemSelected текущая вкладка = ${viewPager.currentItem}")
                val pos = viewModel.getIndexOfCurrentSong()
                Log.d(TAG, "$$$ SongFragment onMenuItemSelected pos = $pos")
                (binding.localRecyclerView.layoutManager as LinearLayoutManager).let{
                    if(pos >=0 ) it.scrollToPositionWithOffset(pos, 0) else it.scrollToPosition(0)
                }
                return true
            }
            R.id.updateSongs->{
                val currentSong = viewModel.getCurrentSong()
                viewModel.refreshLibrary(onScanned = { albumsViewModel.syncAlbums(); artistsViewModel.syncArtist(); foldersViewModel.syncFolders(); playlistsViewModel.loadPlaylists() }) {
                    // Восстанавливаем последнюю песню
                    currentSong?. let{
                        viewModel.reloadCurrentSong(currentSong.id)
                    }
                    Log.d(TAG, "###SongFragment onMenuItemSelected currentSong = ${currentSong?.title} ")
                }
                return  true
            }
            R.id.action_shuffle->{
                val newShuffleState = playerViewModel.setPlaybackMode()
                // Меняем иконку в зависимости от состояния
                if (newShuffleState) {
                    item.applyShuffleIcon(requireContext(), true)
                    Toast.makeText(context, "Случайный порядок включен", Toast.LENGTH_SHORT).show()
                } else {
                    item.applyShuffleIcon(requireContext(), false)
                    Toast.makeText(context, "Прямой порядок включен", Toast.LENGTH_SHORT).show()
                }
                return   true
            }
        }
        return false
    }
}