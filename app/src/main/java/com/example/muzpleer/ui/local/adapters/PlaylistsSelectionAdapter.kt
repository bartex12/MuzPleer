package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemPlaylistSelectionBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

class PlaylistsSelectionAdapter (
    private val viewModel: SharedViewModel,
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistsSelectionAdapter.PlaylistsViewHolder>() {

    var playlists:List<Playlist> = listOf()
    @SuppressLint("NotifyDataSetChanged")
    set(value){
        field = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistsViewHolder {
        val binding = ItemPlaylistSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistsViewHolder, position: Int) {
        holder.bind(playlists[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedAlbumPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount() = playlists.size

    inner class PlaylistsViewHolder(private val binding: ItemPlaylistSelectionBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.playlistTitleSelection.text = playlist.playlistName
            binding.tracksCountSelection.text = getTracksCountString( playlist.playlistSongs.size)

            playlist.playlistArtUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        Log.d(TAG, "!! PlaylistSelectionAdapter bind Glide - Загрузка обложки из  content:/com.android.providers.downloads")
                        showImageWithGlide(binding.root.context, artUri, binding.playlistArtSelection, playlist, binding)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        Log.d(TAG, "!! PlaylistSelectionAdapter bind Glide - Загрузка обложки из  picker")
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.playlistArtSelection, playlist, binding)
                    }else{
                        // Загрузка обложки из кэша приложения
                        Log.d(TAG, "!! PlaylistSelectionAdapter bind Glide - Загрузка обложки из кэша приложения")
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.playlistArtSelection, playlist, binding)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌PlaylistsSelectionAdapter exception when loading: playlist = ${playlist.playlistName} ")
                    binding.playlistArtSelection.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.playlistArtSelection.setImageResource(R.drawable.muz_player3)

            itemView.setOnClickListener {
                viewModel.setSelectedAlbumPosition(absoluteAdapterPosition)
                onPlaylistClick(playlist)
            }
        }
    }

    companion object{
        const val TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView,
                           playlist:Playlist, binding: ItemPlaylistSelectionBinding){
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
                    Log.d(TAG, "❌PlaylistsSelectionAdapter Glide load failed for" +
                            " playlistName = ${playlist.playlistName} URI: $artUri")
                    viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅PlaylistsSelectionAdapter Glide load success for playlistName =$playlist.playlistName URI: $artUri")
                    Log.d(TAG, "✅PlaylistsSelectionAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}