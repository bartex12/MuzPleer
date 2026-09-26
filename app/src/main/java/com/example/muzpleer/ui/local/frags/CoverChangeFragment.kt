package com.example.muzpleer.ui.local.frags

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentCoverChangeBinding
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.example.muzpleer.util.isContentProviderUri
import com.example.muzpleer.util.isContentProviderUriPicker
import java.io.File

class CoverChangeFragment : Fragment() {
    private var _binding: FragmentCoverChangeBinding? = null
    private val binding get() = _binding!!
    private var isExpanded = false
    private var previousTitle: String = "" // Сохраняем предыдущий заголовок
    private val viewModel: SharedViewModel by activityViewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Log.d(TAG, "!!!CoverChangeFragment registerForActivityResult uri = $it ")
            viewModel.updateCoverImage(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoverChangeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Сохраняем текущий заголовок перед сменой
        previousTitle = (requireActivity() as? MainActivity)?.getCurrentTitle() ?: "Музыка на ладони"
        Log.d(TAG, "5*** CoverChangeFragment onViewCreated previousTitle = $previousTitle")

        //обеспечивает установку обложки при открытии CoverChangeFragment и замене обложки через pickImage
        viewModel.coverImageUri.observe(viewLifecycleOwner) { uri ->
            Log.d(TAG, "4*** CoverChangeFragment coverImageUri.observe: uri = $uri ")

            uri?.let{artUri->
                // Загружаем изображение
                try {
                    if (isContentProviderUri(artUri.toString())){
                        // Загрузка обложки из  content:/com.android.providers.downloads
                        showImageWithGlide(binding.root.context, artUri, binding.coverImageView)
                    }else  if (isContentProviderUriPicker(artUri.toString())){
                        // Загрузка обложки из picker
                        val  photoPickerUri =artUri.toString().toUri()
                        showImageWithGlide(binding.root.context, photoPickerUri, binding.coverImageView)
                    }else {
                        // Загрузка обложки из кэша приложения
                        showImageWithGlide(binding.root.context, File(artUri.toString()), binding.coverImageView)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CoverChangeFragment exception when loading: ${e.message}")
                    binding.coverImageView.setImageResource(R.drawable.muz_player3)
                }
            }?: binding.coverImageView.setImageResource(R.drawable.muz_player3)
        }

        binding.usePhonePhotos.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.searchOnline.setOnClickListener {
            openFlickrSearch()
        }

        binding.restoreDefault.setOnClickListener {
            viewModel.restoreDefaultCover()
        }

        binding.saveButton.setOnClickListener {
            viewModel.getCoverImageUri()?. let{curUri->
                Log.d(TAG, "1*** CoverChangeFragment saveButton: curUri = $curUri")
                viewModel.updateCoverImageAndSave(curUri)
            }
            findNavController().navigateUp()
        }

        binding.coverImageView.setOnClickListener {
            isExpanded = !isExpanded
            //
            TransitionManager.beginDelayedTransition(
                binding.coverContainer, TransitionSet()
                    .addTransition(ChangeBounds())
                    .addTransition(ChangeImageTransform())
            )
            val params = binding.coverImageView.layoutParams as ConstraintLayout.LayoutParams
            if (isExpanded) {
                // Оставляем существующие constraints, но меняем размеры
                params.topMargin = 0
                params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                params.dimensionRatio = ""
                binding.coverImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                // Возвращаем оригинальные размеры
                params.topMargin = 16
                params.width = 0
                params.height = 0
                params.dimensionRatio = "1:1"
                params.matchConstraintPercentWidth = 0.6f
                binding.coverImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            }

            binding.coverImageView.layoutParams = params
        }
    }

    override fun onResume() {
        super.onResume()
        // Устанавливаем заголовок
        updateToolbarTitle("Замена обложки")
    }

    override fun onDestroyView() {
        // Восстанавливаем предыдущий заголовок при уходе с фрагмента
        restorePreviousTitle()
        _binding = null
        super.onDestroyView()
    }

    private fun restorePreviousTitle() {
        if (previousTitle.isNotEmpty()) {
            (requireActivity() as? MainActivity)?.updateToolbarTitle(previousTitle)
        }
    }

    private fun updateToolbarTitle(title: String) {
        (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
    }

    private fun openFlickrSearch() {
        val intent = Intent(Intent.ACTION_VIEW, "https://www.flickr.com/search/".toUri())
        startActivity(intent)
    }
    companion object{
        const val  TAG = "33333"
    }

    fun showImageWithGlide(context: Context, artUri: Any, imageView: ImageView){
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
                    Log.e(TAG, " ❌CoverChangeFragment Glide load failed for URI: $artUri", e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "✅CoverChangeFragment Glide load success for URI: $artUri")
                    Log.d(TAG, "✅CoverChangeFragment DataSource: $dataSource") // 👈 Важно! Покажет откуда загружено
                    return false
                }
            })
            .into(imageView)
    }

}