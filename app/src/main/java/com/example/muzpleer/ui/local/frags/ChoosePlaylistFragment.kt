package com.example.muzpleer.ui.local.frags

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentChoosePlaylistBinding
import com.example.muzpleer.ui.local.adapters.PlaylistChooseAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.util.toast
import com.google.android.material.textfield.TextInputLayout
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

        // Настройка адаптера
        adapter = PlaylistChooseAdapter(viewModel, playlistsViewModel) { playlist ->
            playlistsViewModel.addSongToPlaylist(songId, playlist.id)
            Toast.makeText(
                requireContext(),
                "Песня \"$songTitle\" добавлена в \"${playlist.playlistName}\"",
                Toast.LENGTH_SHORT
            ).show()
            // Возвращаемся назад
            findNavController().navigateUp()
        }

        binding.rvPlaylists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChoosePlaylistFragment.adapter
        }

        // Строка поиска плейлиста
        setupSearch()

        // Поиск показываем, только если есть из чего выбирать
        playlistsViewModel.playlists.observe(viewLifecycleOwner) { all ->
            binding.inputLayoutSearch.visibility =
                if (all.any { it.id != -1L }) View.VISIBLE else View.GONE
        }

        // Наблюдаем за списком плейлистов с правильным LifecycleOwner
        playlistsViewModel.filteredPlaylists.observe(viewLifecycleOwner) { playlists ->
            adapter.playlists = playlists.filter { it.id != -1L }
        }

        viewModel.coverImageUriLevel.observe(viewLifecycleOwner) {uri->
            //последняя изменённая обложка плейлиста
            Log.d(TAG, " //**// ChoosePlaylistFragment onViewCreated:  uri = $uri")
        }

        // Кнопка закрытия
        binding.btnClose.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSearch() {
        // Enter на клавиатуре
        binding.inputEditTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                return@setOnEditorActionListener true
            }
            false
        }

        // Поиск при каждом изменении текста
        binding.inputEditTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.let { playlistsViewModel.filterPlaylists(it.toString()) }
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

        // Иконка справа: очистить текст, а если он пуст - выполнить поиск
        binding.inputLayoutSearch.setEndIconOnClickListener {
            if (binding.inputEditTextSearch.text?.isNotEmpty() == true) {
                binding.inputEditTextSearch.text?.clear()
                playlistsViewModel.filterPlaylists("")
            } else {
                performSearch()
            }
        }

        // Кнопка "Назад" при открытой клавиатуре: убрать фокус, очистить поиск
        binding.inputEditTextSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP &&
                binding.inputEditTextSearch.hasFocus()
            ) {
                binding.inputEditTextSearch.clearFocus()
                binding.inputEditTextSearch.isCursorVisible = false
                if (binding.inputEditTextSearch.text?.isNotEmpty() == true) {
                    binding.inputEditTextSearch.text?.clear()
                    playlistsViewModel.filterPlaylists("")
                }
                hideKeyboard()
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun performSearch() {
        val query = binding.inputEditTextSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            playlistsViewModel.filterPlaylists(query)
            hideKeyboard()
        } else {
            toast("Введите текст для поиска")
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputEditTextSearch.windowToken, 0)
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