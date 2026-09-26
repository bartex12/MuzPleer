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
import com.example.muzpleer.model.Artist
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.getTracksCountString
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

class ArtistSelectionAdapter(
    private val viewModel: SharedViewModel,
    private val onArtistClick: (Artist) -> Unit
) : RecyclerView.Adapter<ArtistSelectionAdapter.AlbumViewHolder>() {

    var artists:List<Artist> = listOf()
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
        holder.bind(artists[position])

        // Следим за изменениями выбранной позиции
        viewModel.selectedAlbumPosition
            .observe(holder.itemView.context as LifecycleOwner) { selectedPos ->
                holder.itemView.isSelected = position == selectedPos
            }
    }

    override fun getItemCount() = artists.size

    inner class AlbumViewHolder(private val binding: ItemAlbumSelectionBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(artist: Artist) {
            binding.albumTitleSelection.text = artist.name
            //binding.albumArtistSelection.text = artist.albums
            binding.tracksCountSelection.text = getTracksCountString( artist.songs.size)

            artist.artworkUri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.albumArtSelection, artist, binding)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.albumArtSelection, artist, binding)
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.albumArtSelection, artist, binding)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ArtistSelectionAdapter exception when loading: ${e.message}")
                    binding.albumArtSelection.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.albumArtSelection.setImageResource(R.drawable.muz_player3)

            itemView.setOnClickListener {
                viewModel.setSelectedAlbumPosition(absoluteAdapterPosition)
                onArtistClick(artist)
            }
        }
    }

    companion object{
        const val TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView,
                           artist: Artist, binding: ItemAlbumSelectionBinding){
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
                    Log.d(TAG, "❌ArtistSelectionAdapter Glide load failed for" +
                            " artist Name = ${artist.name} URI: $artUri")
                    viewModel.updateArtistArtUri(binding.root.context, artist)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅ArtistSelectionAdapter Glide load success for URI: $artUri")
                    Log.d(TAG, "✅ArtistSelectionAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

}