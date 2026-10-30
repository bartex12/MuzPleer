package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.findViewTreeLifecycleOwner
import android.util.Log
import com.example.muzpleer.util.audioFormatText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemMusicBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.model.Song
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.ui.local.viewmodel.PlaylistsViewModel
import com.example.muzpleer.ui.local.viewmodel.FavoritesViewModel
import com.example.muzpleer.util.formatAsTime
import com.example.muzpleer.util.formatDate
import com.example.muzpleer.util.formatFileSize
import com.example.muzpleer.util.getNormalizedPath
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File


class SongsAdapter(
    private val viewModel: SharedViewModel,
    private val playlistsViewModel: PlaylistsViewModel,
    private val favoritesViewModel: FavoritesViewModel,
    private val navController: NavController,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongsAdapter.MusicViewHolder>() {

    companion object{
        const val TAG = "33333"
        private const val FAV_PAYLOAD = "fav"
    }

    var data:List<Song> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicBinding.inflate(
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
                    Log.d(TAG, "SongsAdapter Ошибка: ${e.message}")
                }
            }
    }

    //id избранных треков (для значка сердечка на обложке)
    private var favoriteIds: Set<Long> = emptySet()
    private var favObserver: Observer<Set<Long>>? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val owner = recyclerView.findViewTreeLifecycleOwner() ?: return
        val observer = Observer<Set<Long>> { ids ->
            favoriteIds = ids
            notifyItemRangeChanged(0, itemCount, FAV_PAYLOAD)
        }
        favObserver = observer
        favoritesViewModel.favoriteIds.observe(owner, observer)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        favObserver?.let { favoritesViewModel.favoriteIds.removeObserver(it) }
        favObserver = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(FAV_PAYLOAD)) {
            holder.updateFavBadge(data[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class MusicViewHolder(private val binding: ItemMusicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun updateFavBadge(track: Song) {
            binding.favBadge.visibility = if (track.id in favoriteIds) View.VISIBLE else View.GONE
        }

        fun bind(track: Song) {
            updateFavBadge(track)
            Log.d(TAG, "WWW-1 SongAdapter track.title = ${track.title}  ")

            binding.trackTitle.text = track.title
            binding.trackArtist.text = track.artist
            binding.trackDuration.text = track.duration.formatAsTime()

            Log.d(TAG, "WWW 1 SongAdapter bind track.artUri =  ${track.artUri}")
            // Загрузка обложки, если заменили её на другую
            track.artUri?.let {artUri->
                try {
                    if (isContentProviderUri(artUri)){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.trackArtwork, track, binding)
                        Log.d(TAG,"WWW 3 SongAdapter bind  title = ${track.title}")
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.trackArtwork, track, binding)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri), binding.trackArtwork, track, binding)
                    }
                }catch (e: Exception){
                    Log.e(TAG, " ❌SongsAdapter Glide load failed in SongsAdapter for URI: $artUri", e)
                    binding.trackArtwork.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.trackArtwork.setImageResource(R.drawable.muz_player3)

            binding.root.setOnClickListener {
                viewModel.setSelectedPosition(absoluteAdapterPosition)
                onItemClick(track)
            }

            binding.menuButton.setOnClickListener { view ->
                showPopupMenu(view, track)
            }
        }
    }

    private fun showPopupMenu(view: View, song: Song) {
        val context = view.context
        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.popup_song_menu, null)


        val popup =  PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable, чтобы закрывался по тапу вне
        )

        popup.elevation = 8f
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable()) // важно для закрытия тапом вне

        // Видимость пунктов избранного
        val itemAddFav = popupView.findViewById<TextView>(R.id.itemAddToFavorites)
        val itemRemFav = popupView.findViewById<TextView>(R.id.itemRemoveFromFavorites)

        favoritesViewModel.checkIsFavoriteWithCallback(song.id) {isFavorite->
            itemAddFav.isVisible = !isFavorite
            itemRemFav.isVisible = isFavorite
        }

        itemAddFav.setOnClickListener { favoritesViewModel.toggleFavorite(song); popup.dismiss() }
        itemRemFav.setOnClickListener { favoritesViewModel.toggleFavorite(song); popup.dismiss() }
        popupView.findViewById<TextView>(R.id.itemAddToPlaylist).setOnClickListener {
            navigateToChoosePlaylistFragment(context, song); popup.dismiss()
        }

        popupView.findViewById<TextView>(R.id.itemChangeCover).setOnClickListener {
            //сменить обложку
            Log.d(TAG, "!!!SongsAdapter showPopupMenu itemChangeCover:" +
                    "song title = ${song.title} song artUri =  ${song.artUri}")
            //запоминаем во ViewModel выбранную песню  - потом в CoverChange Fragment понадобится
            viewModel.setSelectedSong(song)
            //идём во фрагмент замены обложки с источником вызова адаптера
            view.findNavController().navigate(R.id.coverChangeFragment)
            popup.dismiss()
        }

        popupView.findViewById<TextView>(R.id.itemEditSongInfo).setOnClickListener {
            viewModel.setSelectedSong(song)
            // Переходим к редактированию
            view.findNavController().navigate(R.id.editSongFragment)
            popup.dismiss()
        }

        popupView.findViewById<TextView>(R.id.itemSend).setOnClickListener {
            shareSong(context, song) // Вызов функции для отправки песни
            popup.dismiss()
        }

        popupView.findViewById<TextView>(R.id.itemSongInfo).setOnClickListener {
            showSongInfoDialog(context, song)
            popup.dismiss()
        }

        popupView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )

        popup.showAsDropDown(view, 0, - popupView.measuredHeight)
    }

    /**
     * 🔧 Навигация к ChoosePlaylistFragment с передачей параметров
     */
    private fun navigateToChoosePlaylistFragment(context:Context, song: Song) {
        try {
            // Создаём Bundle с параметрами
            val bundle = Bundle().apply {
                putLong("song_id", song.id)
                putString("song_title", song.title)
            }
            // Навигация с Bundle
            navController.navigate(R.id.choosePlaylistFragment,bundle )
        } catch (e: Exception) {
            Log.e(TAG, "❌SongAdapter navigateToChoosePlaylistFragment Ошибка навигации: ${e.message}")
            // Fallback на диалог
            showChoosePlaylistDialog(context, song)
        }
    }


    private fun showChoosePlaylistDialog(context: Context, song: Song) {
        Log.d(TAG, "//**// SongsAdapter showChoosePlaylistDialog песня  = ${song.title}")
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_choose_playlist)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.setCancelable(true)

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.rvPlaylists)
        val adapter = PlaylistChooseAdapter(viewModel, playlistsViewModel) { playlist ->
            // Добавляем песню в выбранный плейлист
            playlistsViewModel.addSongToPlaylist(song.id, playlist.id) { added ->
                Toast.makeText(
                    context,
                    if (added) "Трек добавлен в \"${playlist.playlistName}\""
                    else context.getString(R.string.song_already_in_playlist),
                    Toast.LENGTH_SHORT
                ).show()
            }

            dialog.dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

//        // Загружаем список плейлистов
//        //используется context as LifecycleOwner для наблюдения за filteredPlaylists,
//        // но диалог не является LifecycleOwner! Наблюдатель никогда не получает
//        // обновлений, поэтому adapter.playlists остаётся пустым.
//        playlistsViewModel.filteredPlaylists.observe(context as LifecycleOwner) { playlists ->
//            adapter.playlists = playlists.filter { it.id != -1L } // Исключаем "Все песни"
//        }
        // 🔧 ИСПРАВЛЕНИЕ: Используем observeForever с ручной отпиской
        val observer = Observer<List<Playlist>> { playlists ->
            adapter.playlists = playlists.filter { it.id != -1L }
        }

        playlistsViewModel.filteredPlaylists.observeForever(observer)

        // Отписываемся при закрытии диалога
        dialog.setOnDismissListener {
            playlistsViewModel.filteredPlaylists.removeObserver(observer)
        }

        dialog.show()
    }

    private fun showSongInfoDialog(context:Context,song: Song) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_song_info)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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
            dialog.findViewById<TextView>(R.id.tvFormat).text =
                audioFormatText(context, song.id, songFile?.path)
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
                putExtra(Intent.EXTRA_TEXT, "Трек: ${song.title}")
            }
            //такой вариант не поддерживается в телеге
            //context.startActivity(Intent.createChooser(shareIntent, "Поделиться песней"))
            // Создаем chooser с заголовком
            val chooserIntent = Intent.createChooser(shareIntent, "Поделиться треком")

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
            Log.d(TAG, "SongsAdapter shareSong песня отправлена: song = ${song.title}")
        } catch (e: Exception) {
            Log.d(TAG, "SongsAdapter shareSong Ошибка при отправке песни: ${e.message}")
            Toast.makeText(context, "Не удалось поделиться треком", Toast.LENGTH_SHORT).show()
        }
    }

    fun showImageWithGlide(context:Context, artUri: Any, imageView: ImageView,
                           track: Song, binding: ItemMusicBinding){
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
                    Log.d(TAG, "❌SongAdapter Glide load failed for" +
                            " track title = ${track.title} URI: $artUri")
                    binding.trackArtwork.setImageResource(R.drawable.muz_player3)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅SongsAdapter Glide load success in SongsAdapter  for title: ${track.title} URI: $artUri")
                    Log.d(TAG, "✅SongsAdapter DataSource in SongsAdapter : $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}
