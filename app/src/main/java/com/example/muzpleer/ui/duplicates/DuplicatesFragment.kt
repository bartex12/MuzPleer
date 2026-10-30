package com.example.muzpleer.ui.duplicates

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentDuplicatesBinding
import com.example.muzpleer.room.dao.SongDao
import com.example.muzpleer.ui.local.viewmodel.AlbumsViewModel
import com.example.muzpleer.ui.local.viewmodel.ArtistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FoldersViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.DuplicateFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Экран «Дубли треков»: показывает копии одного трека и удаляет лишние с телефона (после подтверждения). */
class DuplicatesFragment : Fragment() {
    private var _binding: FragmentDuplicatesBinding? = null
    private val binding get() = _binding!!

    private val songDao: SongDao by inject()
    private val viewModel: SharedViewModel by activityViewModel()
    private val albumsViewModel: AlbumsViewModel by activityViewModel()
    private val artistsViewModel: ArtistsViewModel by activityViewModel()
    private val foldersViewModel: FoldersViewModel by activityViewModel()
    private val playlistsViewModel: PlaylistsViewModel by activityViewModel()

    private val adapter = DupAdapter()
    //копии, которые ещё надо удалить (при Android 10 система спрашивает разрешение по одной)
    private var pendingQ = ArrayDeque<DuplicateFinder.Item>()
    private var deletedCount = 0

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                deletedCount = adapter.selectedItems().size
                finishDeleting()
            } else {
                //Android 10: разрешили удалить одну копию - повторяем удаление остальных
                deleteNext()
            }
        } else {
            Toast.makeText(requireContext(), "Удаление отменено", Toast.LENGTH_SHORT).show()
            load()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDuplicatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvDuplicates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDuplicates.adapter = adapter
        binding.btnDeleteDuplicates.setOnClickListener { confirmDelete() }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                DuplicateFinder.groups(DuplicateFinder.loadItems(requireContext()), songDao.getReferencedIds().toSet())
            }
            _binding ?: return@launch
            adapter.submit(groups)
            binding.tvDupEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            binding.btnDeleteDuplicates.visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE
            binding.tvDupHint.visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun confirmDelete() {
        val items = adapter.selectedItems()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "Ничего не отмечено", Toast.LENGTH_SHORT).show()
            return
        }
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm, null)
        view.findViewById<TextView>(R.id.tvConfirmTitle).text = "Удалить копии?"
        view.findViewById<TextView>(R.id.tvConfirmMessage).text =
            "Будет удалено файлов: ${items.size}. Они пропадут с телефона, вернуть их нельзя. По одной копии каждого трека останется."
        view.findViewById<TextView>(R.id.btnConfirmOk).text = "Удалить"
        val dialog = AlertDialog.Builder(requireContext()).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        view.findViewById<View>(R.id.btnConfirmOk).setOnClickListener { dialog.dismiss(); startDelete(items) }
        view.findViewById<View>(R.id.btnConfirmCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startDelete(items: List<DuplicateFinder.Item>) {
        deletedCount = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android 11+: одно системное окно с подтверждением на все файлы
            val pi = MediaStore.createDeleteRequest(requireContext().contentResolver, items.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            pendingQ = ArrayDeque(items)
            deleteNext()
        }
    }

    //Android 10 и старше: удаляем по одной копии, при необходимости система спрашивает разрешение
    private fun deleteNext() {
        val resolver = requireContext().contentResolver
        while (pendingQ.isNotEmpty()) {
            val item = pendingQ.first()
            try {
                resolver.delete(item.uri, null, null)
                pendingQ.removeFirst()
                deletedCount++
            } catch (e: SecurityException) {
                val sender: IntentSender? = (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                if (sender != null) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    return
                }
                pendingQ.removeFirst()
            }
        }
        finishDeleting()
    }

    private fun finishDeleting() {
        Toast.makeText(requireContext(), "Удалено копий: $deletedCount", Toast.LENGTH_SHORT).show()
        //обновляем списки приложения, как пункт «Обновить данные»
        viewModel.refreshLibrary(onScanned = {
            albumsViewModel.syncAlbums(); artistsViewModel.syncArtist(); foldersViewModel.syncFolders(); playlistsViewModel.loadPlaylists()
        }) { }
        load()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /** Список: заголовок группы и копии с флажками (первая копия группы остаётся и не отмечается). */
    private class DupAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private sealed class Row {
            class Header(val text: String) : Row()
            class Copy(val item: DuplicateFinder.Item, val keep: Boolean, var checked: Boolean) : Row()
        }

        private var rows = listOf<Row>()

        fun submit(groups: List<List<DuplicateFinder.Item>>) {
            rows = groups.flatMap { g ->
                listOf<Row>(Row.Header("${g.first().title.ifBlank { g.first().fileName }} - ${g.first().artist}")) +
                    g.mapIndexed { i, it -> Row.Copy(it, keep = i == 0, checked = i != 0) }
            }
            @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }

        fun selectedItems(): List<DuplicateFinder.Item> =
            rows.filterIsInstance<Row.Copy>().filter { it.checked && !it.keep }.map { it.item }

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return object : RecyclerView.ViewHolder(
                inflater.inflate(if (viewType == 0) R.layout.item_duplicate_header else R.layout.item_duplicate, parent, false)
            ) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            when (val row = rows[position]) {
                is Row.Header -> (v as TextView).text = row.text
                is Row.Copy -> {
                    val cb = v.findViewById<CheckBox>(R.id.cbDup)
                    cb.setOnCheckedChangeListener(null)
                    cb.isChecked = row.checked && !row.keep
                    cb.isEnabled = !row.keep
                    cb.setOnCheckedChangeListener { _, checked -> row.checked = checked }
                    v.findViewById<TextView>(R.id.tvDupName).text =
                        if (row.keep) "${row.item.fileName}  (оставляем)" else row.item.fileName
                    v.findViewById<TextView>(R.id.tvDupInfo).text =
                        "${Formatter.formatShortFileSize(v.context, row.item.size)} · ${row.item.path}"
                    v.setOnClickListener { if (!row.keep) cb.toggle() }
                }
            }
        }
    }
}
