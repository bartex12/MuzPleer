package com.example.muzpleer.ui.local.frags

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import com.example.muzpleer.model.SongAndPlaylist
import com.example.muzpleer.ui.local.adapters.SongsAdapter
import com.example.muzpleer.ui.local.helper.IPreferenceHelper
import com.example.muzpleer.ui.local.helper.PreferenceHelperImpl
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getSortedDataSong
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SongFragment : Fragment() {
    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModel()
    private lateinit var adapter: SongsAdapter
    private var currentSearchQuery = ""
    private lateinit var appPreferences: IPreferenceHelper
    private lateinit var navController: NavController

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

        appPreferences =PreferenceHelperImpl(App.instance)
        navController = findNavController()

        adapter = SongsAdapter(viewModel, navController) { song ->
            //устанавливаем список песен как плейлист
            val playlist=getSortedDataSong(viewModel.getSongs())
            viewModel.setPlaylist(playlist) //устанавливаем список песен как плейлист
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
            val sortedData = getSortedDataSong(filteredSongs)
            adapter.data = sortedData  //передаём данные в адаптер
            //вывод заголовков всех песен
            //Log.d( TAG,"32 SongsFragment onViewCreated sortedData = ${sortedData.map{it.title}} ")
            //Log.d( TAG,"32 SongsFragment onViewCreated sortedData.size = ${sortedData.size} ")
            if (filteredSongs.isEmpty()) {
                binding.imageHolder3.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.imageHolder3.visibility = View.GONE
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
//        viewModel.loading.observe (viewLifecycleOwner) {loading->
//            if(loading){
//                binding.progressBar.visibility = View.VISIBLE
//                binding.imageHolder3.visibility = View.VISIBLE
//            } else {
//                binding.progressBar.visibility = View.GONE
//                binding.imageHolder3.visibility = View.GONE
//            }
//        }
    }

    override fun onResume() {
        super.onResume()
        val pos = viewModel.getPositionSong()
        Log.d( TAG,"1---SongsFragment onResume PositionSong = $pos ")
        //восстанавливаем позицию списка после поворота или возвращения на экран и при новой загрузке
        val manager = binding.localRecyclerView.layoutManager as LinearLayoutManager
        Log.d( TAG,"1---SongsFragment onResume manager = $manager ")
        manager.scrollToPositionWithOffset(pos, 0)
    }

    //запоминаем  позицию списка, на которой сделан клик - на случай поворота экрана
    override fun onPause() {
        super.onPause()
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

        val searchItem: MenuItem = menu.findItem(R.id.search_toolbar)
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
                viewModel.filterSongs(newText.orEmpty())
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

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
                viewModel.scanMedia(){
                    //сначала сканируем телефон и собираем все музыкальные треки в базе, а потом делаем другие вкладки
                    viewModel.syncAlbums()
                    viewModel.syncArtist ()
                    viewModel.syncFolders()
                    viewModel.loadPlaylists()
                    // Восстанавливаем последнюю песню
                    currentSong?. let{
                        viewModel.setCurrentSongById(currentSong.id)
                    }
                    Log.d(TAG, "###SongFragment onMenuItemSelected currentSong = ${currentSong?.title} ")
                }
                return  true
            }
            R.id.action_shuffle->{
                val newShuffleState = viewModel.setPlaybackMode()
                // Меняем иконку в зависимости от состояния
                if (newShuffleState) {
                    item.setIcon(R.drawable.icons8_shuffle_white_24) // подсвеченная иконка
                    Toast.makeText(context, "Случайный порядок включен", Toast.LENGTH_SHORT).show()
                } else {
                    item.setIcon(R.drawable.icons8_data_white_24) // обычная иконка
                    Toast.makeText(context, "Прямой порядок включен", Toast.LENGTH_SHORT).show()
                }
                return   true
            }
        }
        return false
    }
}