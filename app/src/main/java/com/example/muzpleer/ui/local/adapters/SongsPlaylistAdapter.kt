package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
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
import com.example.muzpleer.databinding.ItemMusicPlaylistBinding
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.adapters.touch.ItemTouchHelperAdapter
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.formatAsTime
import com.example.muzpleer.util.formatDate
import com.example.muzpleer.util.formatFileSize
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File
import java.util.Collections

class SongsPlaylistAdapter(   private val viewModel: SharedViewModel,
                              private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongsPlaylistAdapter.MusicViewHolder>(),
    ItemTouchHelperAdapter  {

    companion object{
        const val TAG = "33333"
    }

    private var isEditMode = false
    private lateinit var itemTouchHelper: ItemTouchHelper // Добавляем ссылку
    // Временный список для перетаскивания
    private val dragData = mutableListOf<Song>()

    var data:List<Song> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MusicViewHolder,
        position: Int
    ) {
        holder.bind(data[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedSongPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
        //следим за текущей песней - чтобы при возврате с другой вкладки выделение оставалось
        viewModel.currentSong
            .observe(holder.itemView.context as LifecycleOwner) { currentSong ->
                try{
                    val dataMediaUri = getNormalizedPath(data[position].mediaUri)
                    val currentSongMediaUri = getNormalizedPath(currentSong?.mediaUri.toString())

                    holder.itemView.isSelected = dataMediaUri == currentSongMediaUri
                }catch(e: Exception){
                    Log.d(TAG, "SongsPlaylistAdapter Ошибка: ${e.message}")
                }
            }
    }

    fun getNormalizedPath(uriString: String): String {
        return if (uriString.startsWith("file://")) {
            Uri.decode(uriString.substring(7))
        } else {
            uriString
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class MusicViewHolder(private val binding: ItemMusicPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentSong: Song

        @SuppressLint("ClickableViewAccessibility")
        fun bind(track: Song) {
            currentSong = track

            binding.trackTitlePlaylist.text = track.title
            binding.trackArtistPlaylist.text = track.artist
            binding.trackDurationPlaylist.text = track.duration.formatAsTime()

            track.artUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.trackArtworkPlaylist, track.title)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.trackArtworkPlaylist, track.title)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.trackArtworkPlaylist, track.title)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌SongsPlaylistAdapter exception when loading: ${e.message}")
                    binding.trackArtworkPlaylist.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.trackArtworkPlaylist.setImageResource(R.drawable.muz_player3)

            binding.root.setOnClickListener {
                viewModel.setSelectedPosition(absoluteAdapterPosition)
                onItemClick(track)
            }

            binding.menuButtonPlaylist.setOnClickListener { view ->
                showPopupMenu(view, track)
            }

            // Показываем/скрываем иконку перетаскивания в режиме редактирования
            if (isEditMode) {
                binding.dragHandlePlaylist.visibility = View.VISIBLE
                binding.menuButtonPlaylist.visibility = View.GONE

                // Добавляем слушатель касаний для иконки перетаскивания
                binding.dragHandlePlaylist.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // Запускаем перетаскивание при нажатии на иконку
                        itemTouchHelper.startDrag(this)
                    }
                    false
                }
            } else {
                binding.dragHandlePlaylist.visibility = View.GONE
                binding.menuButtonPlaylist.visibility = View.VISIBLE
                binding.dragHandlePlaylist.setOnTouchListener(null) // Убираем слушатель
            }
        }
    }

    private fun showPopupMenu(view: View, song: Song) {
        val context = view.context
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.song_playlist_item_menu, popup.menu)

        viewModel.checkIsFavoriteWithCallback(song.id) {isFavorite->
            popup.menu.findItem(R.id.action_add_to_favorites_playlist).isVisible = !isFavorite
            popup.menu.findItem(R.id.action_remove_from_favorites_playlist).isVisible = isFavorite
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_to_favorites_playlist -> {
                    viewModel.toggleFavorite(song)
                    true
                }
                R.id.action_remove_from_favorites_playlist -> {
                    viewModel.toggleFavorite(song)
                    true
                }

                R.id.action_delete_from_playlist -> {
                    // Удаляем песню из плейлиста
                    viewModel.deleteSongFromPlaylist(song.id)
                    // Показываем уведомление
                    Toast.makeText(context, "Песня удалена из плейлиста", Toast.LENGTH_SHORT).show()
                    true
                }

                R.id.action_change_cover_playlist -> {  //сменить обложку
                    Log.d(TAG, "!!!SongsAdapter showPopupMenu action_change_cover:" +
                            "song title = ${song.title} song artUri =  ${song.artUri}")
                    //запоминаем во ViewModel выбранную песню
                    viewModel.setSelectedSong(song)
                    //идём во фрагмент замены обложки с источником вызова адаптера
                    view.findNavController().navigate(R.id.coverChangeFragment)
                    true
                }
                R.id.edit_song_info_playlist -> { //изменить информацию о песне
                    viewModel.setSelectedSong(song)
                    // Переходим к редактированию
                    view.findNavController().navigate(R.id.editSongFragment)
                    true
                }
                R.id.action_send_playlist -> {
                    viewModel.getSongById(song.id){song->
                     song?. let{
                         shareSong(context, it) // Вызов функции для отправки песни
                     }  ?: Log.d(TAG, "!!!SongsAdapter showPopupMenu action_change_cover Песня не найдена: = ${song?.title} ")
                    }
                    true
                }
                R.id.  action_song_info_playlist -> {
                    showSongInfoDialog(context, song)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showSongInfoDialog(context:Context,song: Song) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_song_info)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        // Заполняем данные
        dialog.findViewById<TextView>(R.id.tvTitle).text = song.title ?: "Неизвестно"
        dialog.findViewById<TextView>(R.id.tvArtist).text = song.artist ?: "Неизвестно"
        dialog.findViewById<TextView>(R.id.tvAlbum).text = song.albumName ?: "Неизвестно"
        dialog.findViewById<TextView>(R.id.tvAuthor).text = song.author ?: "Неизвестно"
        dialog.findViewById<TextView>(R.id.tvLocation).text = song.folderPath ?: "Неизвестно"

        // Для размера файла и дат нужно получить полную информацию из базы
        viewModel.getSongDetails(song.id) { songFile ->
            dialog.findViewById<TextView>(R.id.tvSize).text =
                formatFileSize(songFile?.size ?: 0)
            dialog.findViewById<TextView>(R.id.tvYear).text =
                songFile?.year?.toString() ?: "Неизвестно"
            dialog.findViewById<TextView>(R.id.tvDateAdded).text =
                formatDate(songFile?.dateAdded ?: 0)
            dialog.findViewById<TextView>(R.id.tvDateModified).text =
                formatDate(songFile?.lastModified ?: 0)
        }
        // Обработка кнопки
        dialog.findViewById<Button>(R.id.btnUnderstand).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    fun shareSong(context:Context, song: Song) {
        try {
            // Создаем URI для файла
            val file = File(song.mediaUri)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Создаем интент для отправки песни
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Добавляем дополнительную информацию о песне
                putExtra(Intent.EXTRA_SUBJECT, song.title)
                putExtra(Intent.EXTRA_TEXT, "Песня: ${song.title}")
            }
            //такой вариант не поддерживается в телеге
            //context.startActivity(Intent.createChooser(shareIntent, "Поделиться песней"))
            // Создаем chooser с заголовком
            val chooserIntent = Intent.createChooser(shareIntent, "Поделиться песней")

            // Предоставляем временные права доступа
            val resInfoList = context.packageManager
                .queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)

            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            // Запускаем chooser
            context.startActivity(chooserIntent)
            Log.d(TAG, "SongsPlaylistAdapter shareSong песня отправляется: song = ${song.title}")
        } catch (e: Exception) {
            Log.d(TAG, "SongsPlaylistAdapter shareSong Ошибка при отправке песни: ${e.message}")
            Toast.makeText(context, "Не удалось поделиться песней", Toast.LENGTH_SHORT).show()
        }
    }

    fun setEditMode(enable: Boolean) {
        isEditMode = enable
        if (enable) {
            // Инициализируем временный список
            dragData.clear()
            dragData.addAll(data)
        }
        notifyDataSetChanged() // Перерисовываем для показа/скрытия иконки перетаскивания
    }

    // Метод для установки ItemTouchHelper
    fun setItemTouchHelper(helper: ItemTouchHelper) {
        itemTouchHelper = helper
    }

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

    override fun onItemDismiss(position: Int) {
        // Не используется, но должен быть реализован
    }

    override fun onDrop() {
        Log.d(TAG, "--**-- SongsPlaylistAdapter onDrop")
        // Сохраняем окончательный порядок
        data = dragData.toList()

        //обновляем в базе
        viewModel.updatePlaylistSongsOrder(data) { success ->
            if (success) {
                Log.d(TAG, "✅ Порядок плейлиста успешно обновлен")
                // Если нужно, обновляем текущий плейлист с новым порядком
                viewModel.refreshCurrentPlaylist()
            } else {
                Log.e(TAG, "❌ Ошибка обновления порядка плейлиста")
            }
        }
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView, title:String){
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
                    Log.e(TAG, " ❌SongsPlaylistAdapter Glide load failed for title = $title URI: $artUri", e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅SongsPlaylistAdapter Glide load success for title = $title URI: $artUri")
                    Log.d(TAG, "✅SongsPlaylistAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}
