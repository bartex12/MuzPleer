package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemPlaylistBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.TabLocalFragmentDirections
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperAdapter
import com.example.muzpleer.ui.local.frags.CoverChangeLevelFragment.LevelType
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.util.Collections

class PlaylistAdapter(
    private val viewModel: SharedViewModel,
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>(),
    ItemTouchHelperAdapter  {

    private var isEditMode = false
    private lateinit var itemTouchHelper: ItemTouchHelper // Добавляем ссылку
    // Временный список для перетаскивания
    private val dragData = mutableListOf<Playlist>()

    @SuppressLint("NotifyDataSetChanged")
    var playlist: List<Playlist> = listOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding=ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlist[position])
        // Следим за изменениями выбранной позиции
        viewModel.selectedPlaylistPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount()=playlist.size

    inner class PlaylistViewHolder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(playlist: Playlist) {
            binding.playlistTitle.text=playlist.playlistName
            //todo время треков
            binding.playlistSubtitle.text=getTracksCountString(playlist.playlistSongs.size)

            playlist.playlistArtUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.playlistArtwork, playlist.playlistName){
                            // При ошибке сбрасываем в БД
                            viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                        }
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.playlistArtwork, playlist.playlistName){
                            // При ошибке сбрасываем в БД
                            viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                        }
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.playlistArtwork, playlist.playlistName){
                            // При ошибке сбрасываем в БД
                            viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌PlaylistAdapter exception when loading: ${e.message}")
                    binding.playlistArtwork.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.playlistArtwork.setImageResource(R.drawable.muz_player3)

            binding.playlistMenuButton.setOnClickListener { view ->
                showPopupMenu(view, playlist)
            }

            itemView.setOnClickListener {
                viewModel.setSelectedPlaylistPosition(absoluteAdapterPosition)
                onPlaylistClick(playlist)
            }

            // Показываем/скрываем иконку перетаскивания в режиме редактирования
            if (isEditMode) {
                binding.playlistDragHandle.visibility = View.VISIBLE
                binding.playlistMenuButton.visibility = View.GONE
                // Добавляем слушатель касаний для иконки перетаскивания
                binding.playlistDragHandle.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // Запускаем перетаскивание при нажатии на иконку
                        itemTouchHelper.startDrag(this)
                    }
                    false
                }
            } else {
                binding.playlistDragHandle.visibility = View.GONE
                binding.playlistMenuButton.visibility = View.VISIBLE
                binding.playlistDragHandle.setOnTouchListener(null) // Убираем слушатель
            }
        }
    }

    private fun showPopupMenu(view: View, playlist: Playlist) {
        val context = view.context
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.playlist_item_menu, popup.menu)

        // Скрываем или показываем пункт в зависимости от содержимого
        val deleteSongsItem = popup.menu.findItem(R.id.delete_song_from_playlist)
        if (playlist.playlistSongs.isEmpty()) {
            // Если пусто - меняем текст и отключаем
            deleteSongsItem?.title = "Плейлист пуст"
            deleteSongsItem?.isEnabled = false
        } else {
            // Если есть песни - показываем нормальный текст
            deleteSongsItem?.title = "Удалить песни из плейлиста"
            deleteSongsItem?.isEnabled = true
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {

                R.id.add_song_to_playlist -> {  //добавить песню в плейлист
                    navigateToAddSongsSource(view, playlist)
                    true
                }
                R.id.action_change_cover_playlist -> {  //изменить обложку плейлиста
                    navigateToChangePlaylistCover(view, playlist)
                    true
                }
                R.id.rename_playlist -> { //переименовать плейлист
                    showRenameDialog(context, playlist)
                    true
                }
                R.id.delete_song_from_playlist -> {
                    if (playlist.playlistSongs.isNotEmpty()) {
                        navigateToRemoveSongsFromPlaylist(view, playlist)
                        true
                    } else {
                        // Просто игнорируем нажатие на неактивный пункт
                        false
                    }
                }
                R.id.delete_playlist -> {  //удалить плейлист
                    showDeleteDialog(context, playlist)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // Новый метод для навигации к удалению песен
    private fun navigateToRemoveSongsFromPlaylist(view: View, playlist: Playlist) {
        // Создаем Bundle для передачи данных
        val bundle = Bundle().apply {
            putLong("playlistId", playlist.id)
            putString("playlistName", playlist.playlistName)
        }

        // Используем существующий ID фрагмента
        view.findNavController().navigate(
            R.id.action_tabLocalFragment_to_removeSongsFromPlaylistFragment,
            bundle
        )
    }


    fun showDeleteDialog(context:Context, playlist: Playlist) {
        val deleteDialog = AlertDialog.Builder(context)
        deleteDialog.setTitle("Удалить: Вы уверены?")
        deleteDialog.setPositiveButton("Нет") { _, _ ->
            //ничего не делаем
            deleteDialog.create().dismiss()
        }
        deleteDialog.setNegativeButton("Да" ) { _, _ -> //поручаем удаление файла ViewModel
            viewModel.deletePlaylist(playlist.id)
        }
        deleteDialog.show()
    }

    private fun showRenameDialog(context: Context, playlist: Playlist) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_playlist, null)
        val editText = dialogView.findViewById<EditText>(R.id.rename_edit_text)
        val textLayout = dialogView.findViewById<TextInputLayout>(R.id.rename_text_layout)

        editText.setText(playlist.playlistName)
        editText.setSelection(playlist.playlistName.length)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Переименовать плейлист")
            .setView(dialogView)
            .setPositiveButton("Сохранить", null) // Обработчик установим позже
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        // Показываем клавиатуру
        dialog.setOnShowListener {
            editText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }

        // Обработка кнопки Сохранить с валидацией
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val newName = editText.text.toString().trim()

                when {
                    newName.isEmpty() -> {
                        textLayout.error = "Название не может быть пустым"
                    }
                    newName == playlist.playlistName -> {
                        dialog.dismiss()
                    }
                    else -> {
                        viewModel.renamePlaylist(playlist.id, newName)
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun navigateToAddSongsSource(view: View,  playlist: Playlist) {
        val action =TabLocalFragmentDirections.actionTabLocalFragmentToAddSongsSourceFragment(
            playlistId = playlist.id
        )
        view.findNavController().navigate(action)
    }

    private fun navigateToChangePlaylistCover(view: View,  playlist: Playlist) {
        viewModel.setCurrentPlaylistByPlaylistId(playlist.id)
        val bundle: Bundle  = Bundle().apply{
            putLong("levelId", playlist.id)
            putSerializable("levelType", LevelType.PLAYLIST)
        }
        view.findNavController().navigate(R.id.coverChangeLevelFragment, bundle)
    }

    fun setEditMode(enable: Boolean) {
        isEditMode = enable
        if (enable) {
            // Инициализируем временный список
            dragData.clear()
            dragData.addAll(playlist)
        }
        notifyDataSetChanged() // Перерисовываем для показа/скрытия иконки перетаскивания
    }

    // Метод для установки ItemTouchHelper
    fun setItemTouchHelper(helper: ItemTouchHelper) {
        itemTouchHelper = helper
    }

    //пошли методы от интерфейса ItemTouchHelperAdapter
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // Работаем с временным списком
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(dragData, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(dragData, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)

        return true
    }

    override fun onDrop() {
        // Сохраняем окончательный порядок
        playlist = dragData.toList()
        viewModel.updatePlaylistOrder(playlist)
    }

    override fun onItemDismiss(position: Int) {
        // Не используется, но должен быть реализован
    }

    companion object{
        const val TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView, playlistName:String, onError:((Exception?)-> Unit)?= null){
        // Загрузка обложки
        Glide.with(context)
            .load(artUri)
            .placeholder(R.drawable.muz_player3)
            .error(R.drawable.muz_player3)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable?>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e(TAG, "❌ PlaylistAdapter   showImageWithGlide Glide load failed for playlistName $playlistName   URI: $artUri", e)
                    onError?.invoke(e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅PlaylistAdapter   showImageWithGlide Glide load success for playlistName $playlistName   URI: $artUri")
                    Log.d(TAG, "✅PlaylistAdapter  showImageWithGlide DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

}

