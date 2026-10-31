package com.example.muzpleer.ui.local.frags

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentChoosePlaylistBinding
import com.example.muzpleer.ui.local.adapters.PlaylistChooseAdapter
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ChoosePlaylistFragment :Fragment()  {

    private var _binding: FragmentChoosePlaylistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModel()

    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private lateinit var adapter: PlaylistChooseAdapter
    private var songId: Long = -1L
    private var songTitle: String = ""

    companion object {
        const val TAG = "33333"
        const val TAG1 = "ChoosePlaylistFragment"
        private const val ARG_SONG_ID = "song_id"
        private const val ARG_SONG_TITLE = "song_title"

        fun newInstance(songId: Long, songTitle: String): ChoosePlaylistFragment {
            return ChoosePlaylistFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SONG_ID, songId)
                    putString(ARG_SONG_TITLE, songTitle)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔧 Получаем параметры из Bundle
        arguments?.let {
            songId = it.getLong(ARG_SONG_ID, -1L)
            songTitle = it.getString(ARG_SONG_TITLE, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChoosePlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, " //**// ChoosePlaylistFragment onViewCreated:  ")

        updateToolbarTitle("Выбор плейлиста")
        setHasOptionsMenu(true)

        // Настройка адаптера
        adapter = PlaylistChooseAdapter(viewModel, playlistsViewModel) { playlist ->
            // Контекст берём заранее: после navigateUp фрагмент уже отсоединён, а результат приходит позже
            val ctx = requireContext().applicationContext
            val title = songTitle
            playlistsViewModel.addSongToPlaylist(songId, playlist.id) { added ->
                Toast.makeText(
                    ctx,
                    if (added) "Трек \"$title\" добавлен в \"${playlist.playlistName}\""
                    else ctx.getString(R.string.song_already_in_playlist),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Возвращаемся назад
            findNavController().navigateUp()
        }

        binding.rvPlaylists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChoosePlaylistFragment.adapter
        }

        // Поиск показываем, только если есть из чего выбирать; без плейлистов - надпись
        playlistsViewModel.playlists.observe(viewLifecycleOwner) { all ->
            val hasPlaylists = all.any { it.id != -1L }
            //плейлистов нет вообще: вместо пустого списка надпись
            binding.tvNoPlaylists.visibility = if (hasPlaylists) View.GONE else View.VISIBLE
            binding.rvPlaylists.visibility = if (hasPlaylists) View.VISIBLE else View.GONE
            if (!hasPlaylists) binding.tvEmptyPlaylistsSelect.visibility = View.GONE
        }

        // Наблюдаем за списком плейлистов с правильным LifecycleOwner
        playlistsViewModel.filteredPlaylists.observe(viewLifecycleOwner) { playlists ->
            adapter.playlists = playlists.filter { it.id != -1L }
            //«ничего не найдено» только если плейлисты есть, но поиск их отсёк
            val hasAny = playlistsViewModel.playlists.value?.any { it.id != -1L } == true
            binding.tvEmptyPlaylistsSelect.visibility =
                if (playlists.isEmpty() && hasAny) View.VISIBLE else View.GONE
        }

        viewModel.coverImageUriLevel.observe(viewLifecycleOwner) {uri->
            //последняя изменённая обложка плейлиста
            Log.d(TAG, " //**// ChoosePlaylistFragment onViewCreated:  uri = $uri")
        }

        // Новый плейлист: создаём и сразу кладём в него песню, затем возвращаемся в список, откуда пришли
        binding.btnCreatePlaylist.setOnClickListener {
            val ctx = requireContext().applicationContext
            val nav = findNavController()
            val title = songTitle
            val id = songId
            com.example.muzpleer.util.showCreatePlaylistDialog(requireContext()) { name ->
                playlistsViewModel.createPlaylist(name) { newId ->
                    playlistsViewModel.addSongToPlaylist(id, newId) { added ->
                        Toast.makeText(
                            ctx,
                            if (added) "Трек \"$title\" добавлен в \"$name\""
                            else ctx.getString(R.string.song_already_in_playlist),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (isAdded) nav.navigateUp()
                }
            }
        }

        // Кнопка закрытия
        binding.btnClose.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    //поиск плейлиста в тулбаре, как в списке папок
    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        inflater.inflate(R.menu.menu_choose_playlist, menu)
        val searchItem = menu.findItem(R.id.search_toolbar_choose)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
        searchView.setIconifiedByDefault(true)
        searchView.queryHint = getString(R.string.search_playlist)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                playlistsViewModel.filterPlaylists(newText.orEmpty())
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem) = true
            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                playlistsViewModel.filterPlaylists("")
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //фильтр общий для списка плейлистов: уходя, показываем все плейлисты
        playlistsViewModel.filterPlaylists("")
        // Сбрасываем заголовок при уходе с фрагмента
        (requireActivity() as? MainActivity)?.resetToTabTitle()
        _binding = null
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }
}