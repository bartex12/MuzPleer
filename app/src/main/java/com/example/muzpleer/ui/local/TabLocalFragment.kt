package com.example.muzpleer.ui.local

import com.example.muzpleer.util.accentColor
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.databinding.FragmentTabslocalBinding
import com.example.muzpleer.ui.local.adapters.ViewPageAdapterLocal
import com.example.muzpleer.ui.local.viewmodel.AlbumsViewModel
import com.example.muzpleer.ui.local.viewmodel.ArtistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class TabLocalFragment: Fragment()  {
    companion object{
        const val TAG = "33333"
    }
    private var _binding: FragmentTabslocalBinding? = null
    private val binding get() = _binding!!

    lateinit var viewPager: ViewPager
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: ViewPageAdapterLocal
    private val viewModel: SharedViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private val favoritesViewModel: FavoritesViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val albumsViewModel: AlbumsViewModel by activityViewModel()
    private val artistsViewModel: ArtistsViewModel by activityViewModel()

    //число строк в списке каждой вкладки (без учёта поиска); null - данные ещё не загружены
    private val counts = arrayOfNulls<Int>(6)

    //заголовок тулбара: название вкладки и число строк, например «Все треки(760)»
    private fun toolbarTitle(position: Int): String? {
        val name = tabLayout.getTabAt(position)?.text?.toString()
        if (name.isNullOrEmpty()) return null
        return counts.getOrNull(position)?.let { "$name($it)" } ?: name
    }

    fun currentToolbarTitle(): String? = if (_binding == null) null else toolbarTitle(viewPager.currentItem)

    private fun refreshToolbarTitle() {
        if (_binding == null) return
        toolbarTitle(viewPager.currentItem)?.let { (requireActivity() as? MainActivity)?.updateToolbarTitle(it) }
    }

    private fun observeCounts() {
        val owner = viewLifecycleOwner
        viewModel.songs.observe(owner) { counts[0] = it?.size; refreshToolbarTitle() }
        foldersViewModel.folders.observe(owner) { counts[1] = it?.size; refreshToolbarTitle() }
        favoritesViewModel.favoriteSongs.observe(owner) { counts[2] = it?.size; refreshToolbarTitle() }
        playlistsViewModel.playlists.observe(owner) { counts[3] = it?.size; refreshToolbarTitle() }
        albumsViewModel.albums.observe(owner) { counts[4] = it?.size; refreshToolbarTitle() }
        artistsViewModel.artists.observe(owner) { counts[5] = it?.size; refreshToolbarTitle() }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabslocalBinding.inflate(inflater, container, false )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "#TabLocalFragment onViewCreated:  ")
        initViews()
        initPageAdapter()
        observeCounts()
        //устанавливаем текущую вкладку - берём из преференсис
        viewPager.currentItem  =  viewModel.getTabsLocalPosition()

        // Устанавливаем слушатель на TabLayout
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val title = tab?.let { toolbarTitle(it.position) } ?: ""
                Log.d(TAG, "#!# TabLocalFragment onViewCreated onTabSelected title = $title  ")
                (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Не используем
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Не используем
            }
        })
    }

    override fun onResume() {
        super.onResume()
        //при возврате с других экранов (удаление треков, выбор и т.п.) в тулбаре мог остаться чужой заголовок
        val title = toolbarTitle(viewPager.currentItem)
        if (!title.isNullOrEmpty()) {
            (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
        }
    }

    override fun onPause() {
        super.onPause()
        //запоминаем текущую вкладку
        viewModel.saveTabsLocalPosition(viewPager.currentItem)
    }

    private fun initViews() {
        viewPager = binding.viewPagerLocal
        tabLayout = binding.tabLayoutLocal
    }

    private fun initPageAdapter() {
        adapter =  ViewPageAdapterLocal(requireActivity(), childFragmentManager, viewPager)
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)

        val normalColor = Color.WHITE
        val selectedColor = binding.root.context.accentColor()
        // val selectedColor =  Color.BLUE
        //устанавливаем цвет текста белый а при выделении - colorSecondaryBlue
        tabLayout.setTabTextColors(normalColor,  selectedColor)
        tabLayout.setSelectedTabIndicatorColor(selectedColor)
    }
}