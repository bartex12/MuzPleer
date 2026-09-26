package com.example.muzpleer.ui.local.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.R
import com.example.muzpleer.databinding.ItemPlaylistChooseBinding
import com.example.muzpleer.model.Playlist
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

//для диалога выбора плейлиста для песни
class PlaylistChooseAdapter(
    private val viewModel:SharedViewModel,
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistChooseAdapter.PlaylistViewHolder>() {

    var playlists: List<Playlist> = listOf()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistChooseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    override fun getItemCount(): Int = playlists.size

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistChooseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.tvPlaylistName.text = playlist.playlistName
            binding.tvSongsCount.text = "${playlist.songCount} песен"

            // 🔧 Добавляем логирование
            Log.d(TAG, "**%%** PlaylistChooseAdapter bind Playlist: ${playlist.playlistName}, artUri: ${playlist.playlistArtUri}")

            playlist.playlistArtUri?.let{artUri->
                Log.d(TAG, "PlaylistChooseAdapter bind artUri toString: $artUri")
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.ivPlaylistArt, playlist.playlistName){
                            // При ошибке сбрасываем в БД
                            viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                        }
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.ivPlaylistArt, playlist.playlistName){
                            // При ошибке сбрасываем в БД
                            viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                        }
                    }else{
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.ivPlaylistArt, playlist.playlistName){
                            // При ошибке сбрасываем в БД
                            viewModel.updatePlaylistArtUri(binding.root.context, playlist)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌PlaylistChooseAdapter exception when loading: ${e.message}")
                    binding.ivPlaylistArt.setImageResource(R.drawable.muz_player2)
                }
            }?: binding.ivPlaylistArt.setImageResource(R.drawable.muz_player5)

            binding.root.setOnClickListener {
                onPlaylistClick(playlist)
            }
        }
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView, title:String, onError:((Exception?)-> Unit)?= null){
        // Загрузка обложки
        Glide.with(context)
            .load(artUri)
            .placeholder(R.drawable.muz_player3)
            .error(R.drawable.muz_player4)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable?>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e(TAG, "❌PlaylistChooseAdapter Glide load failed for title: $title URI: $artUri", e)
                    onError?.invoke(e)
                    //binding.ivPlaylistArt.setImageResource(R.drawable.muz_player2)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅PlaylistChooseAdapter Glide load success for title: $title URI: $artUri")
                    Log.d(TAG, "✅PlaylistChooseAdapter DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

    companion object{
        const val TAG = "33333"
    }
}