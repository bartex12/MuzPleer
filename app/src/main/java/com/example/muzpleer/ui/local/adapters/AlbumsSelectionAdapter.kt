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
import com.example.muzpleer.databinding.ItemAlbumSelectionBinding
import com.example.muzpleer.model.Album
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

class AlbumsSelectionAdapter(
    private val viewModel: SharedViewModel,
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumsSelectionAdapter.AlbumViewHolder>() {

    var albums:List<Album> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value){
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(albums[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedAlbumPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount() = albums.size

    inner class AlbumViewHolder(private val binding: ItemAlbumSelectionBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.albumTitleSelection.text = album.title
            binding.albumArtistSelection.text = album.artist
            binding.tracksCountSelection.text = getTracksCountString( album.songs.size)

            album.artworkUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.albumArtSelection, album, binding)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.albumArtSelection, album, binding)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.albumArtSelection, album, binding)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌AlbumsSelectionAdapter exception when loading: ${e.message}")
                    binding.albumArtSelection.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.albumArtSelection.setImageResource(R.drawable.muz_player3)

            itemView.setOnClickListener {
                viewModel.setSelectedAlbumPosition(absoluteAdapterPosition)
                onAlbumClick(album)
            }
        }
    }

    companion object{
        const val TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView,
                           album: Album, binding:ItemAlbumSelectionBinding){
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
                    Log.d(TAG, "❌AlbumsSelectionAdapter Glide load failed for" +
                            " album Name = ${album.title} URI: $artUri")
                    viewModel.updateAlbumArtUri(binding.root.context, album)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅AlbumsSelectionAdapter Glide load success for URI: $artUri")
                    Log.d(TAG, "✅AlbumsSelectionAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }
}