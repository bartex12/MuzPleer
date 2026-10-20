package com.example.muzpleer.ui.download

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentDownloadBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.viewmodel.AlbumsViewModel
import com.example.muzpleer.ui.local.viewmodel.ArtistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Экран "Скачать трек": ссылка на mp3 (или на страницу трека suno.com) -> файл в Music/MuzPleer,
 * по желанию сразу добавляется в выбранный плейлист.
 */
class DownloadFragment : Fragment() {

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private val downloadViewModel: DownloadViewModel by activityViewModel()
    private val sharedViewModel: SharedViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()
    private val albumsViewModel: AlbumsViewModel by activityViewModel()
    private val artistsViewModel: ArtistsViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()

    private var playlists: List<Playlist> = emptyList()

    //до Android 10 для записи в общую папку Music нужно разрешение
    private val writePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val picked = pendingPicked
                pendingPicked = null
                if (picked != null) startImport(picked) else startDownload()
            } else {
                pendingPicked = null
                Toast.makeText(requireContext(), R.string.download_need_permission, Toast.LENGTH_LONG).show()
            }
        }

    //выбранный файл, пока запрашивается разрешение на запись (Android 9 и ниже)
    private var pendingPicked: Uri? = null

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onFilePicked(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPaste.setOnClickListener {
            val clip = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clip.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(requireContext())
            if (!text.isNullOrBlank()) binding.etUrl.setText(text.toString().trim())
        }
        binding.btnPickFile.setOnClickListener {
            if (!downloadViewModel.isBusy()) pickAudio.launch(arrayOf("audio/*", "application/octet-stream"))
        }
        binding.btnDownload.setOnClickListener {
            if (downloadViewModel.isBusy()) downloadViewModel.cancel() else onDownloadClicked()
        }

        playlistsViewModel.playlists.observe(viewLifecycleOwner) { list ->
            playlists = list
            val names = listOf(getString(R.string.download_no_playlist)) + list.map { it.playlistName }
            val selected = binding.spPlaylist.selectedItemPosition.coerceAtLeast(0)
            binding.spPlaylist.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
            binding.spPlaylist.setSelection(selected.coerceAtMost(names.size - 1))
        }
        playlistsViewModel.loadPlaylists()

        downloadViewModel.state.observe(viewLifecycleOwner) { render(it) }
    }

    private fun onDownloadClicked() {
        val url = binding.etUrl.text?.toString().orEmpty().trim()
        if (url.isEmpty()) {
            binding.etUrl.error = getString(R.string.download_enter_url)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        startDownload()
    }

    private fun needWritePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false
        val state = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return state != PackageManager.PERMISSION_GRANTED
    }

    private fun onFilePicked(uri: Uri) {
        if (needWritePermission()) {
            pendingPicked = uri
            writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else startImport(uri)
    }

    private fun startImport(uri: Uri) {
        val url = binding.etUrl.text?.toString().orEmpty().trim()
        val pos = binding.spPlaylist.selectedItemPosition
        val playlistId = if (pos > 0) playlists.getOrNull(pos - 1)?.id else null
        downloadViewModel.importFile(uri, url, playlistId)
    }

    private fun startDownload() {
        val url = binding.etUrl.text?.toString().orEmpty().trim()
        val pos = binding.spPlaylist.selectedItemPosition
        val playlistId = if (pos > 0) playlists.getOrNull(pos - 1)?.id else null
        downloadViewModel.start(url, playlistId)
    }

    private fun render(state: DownloadState) {
        val loading = state is DownloadState.Loading
        binding.btnDownload.setText(if (loading) R.string.download_cancel else R.string.download_start)
        binding.etUrl.isEnabled = !loading
        binding.btnPickFile.isEnabled = !loading
        binding.spPlaylist.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        when (state) {
            is DownloadState.Idle -> binding.tvStatus.text = ""
            is DownloadState.Loading -> {
                binding.progress.isIndeterminate = state.percent < 0
                if (state.percent >= 0) binding.progress.progress = state.percent
                val name = state.title?.let { "«$it»: " }.orEmpty()
                binding.tvStatus.text = name + if (state.percent >= 0) "${state.percent}%" else getString(R.string.download_loading)
            }
            is DownloadState.Error -> {
                binding.tvStatus.text = state.message
                downloadViewModel.reset()
            }
            is DownloadState.Done -> {
                binding.tvStatus.text = getString(R.string.download_done, state.track.title)
                onDownloaded(state)
                if (state.track.alreadyExisted) binding.tvStatus.text = getString(R.string.download_exists, state.track.title)
                downloadViewModel.reset()
            }
        }
    }

    /** Файл уже в MediaStore: обновляем медиатеку приложения и, если выбран плейлист, добавляем в него. */
    private fun onDownloaded(done: DownloadState.Done) {
        if (done.track.alreadyExisted) {
            //копию не создавали; при необходимости только добавляем имеющийся трек в плейлист
            done.playlistId?.let { playlistsViewModel.addToPlaylist(it, listOf(done.track.mediaId)) }
            binding.tvStatus.text = getString(R.string.download_exists, done.track.title)
            Toast.makeText(requireContext(), getString(R.string.download_exists, done.track.title), Toast.LENGTH_LONG).show()
            return
        }
        sharedViewModel.refreshLibrary(onScanned = {
            albumsViewModel.syncAlbums()
            artistsViewModel.syncArtist()
            foldersViewModel.syncFolders()
            playlistsViewModel.loadPlaylists()
        }) {
            val addToPlaylist = {
                done.playlistId?.let { playlistsViewModel.addToPlaylist(it, listOf(done.track.mediaId)) }
                Unit
            }
            val cover = done.track.coverPath
            if (cover != null) sharedViewModel.setSongCover(done.track.mediaId, cover, addToPlaylist)
            else addToPlaylist()
        }
        Toast.makeText(requireContext(), getString(R.string.download_done, done.track.title), Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
