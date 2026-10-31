package com.example.muzpleer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import com.example.muzpleer.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Чипы быстрого поиска картинки для обложки в интернете.
 * [query] - строка, которая подставляется в поиск (название трека, альбома и т.п.).
 */
object CoverWebSearch {

    fun setup(group: ChipGroup, query: String) {
        val ctx = group.context
        val q = Uri.encode(query.trim())
        val hasQuery = query.isNotBlank()
        group.removeAllViews()

        //поиск картинок в Google сразу по названию
        if (hasQuery) {
            addChip(group, ctx.getString(R.string.cover_search_google_title),
                "https://www.google.com/search?tbm=isch&q=$q")
        }
        //поисковая строка Google - пользователь вводит запрос сам
        addChip(group, ctx.getString(R.string.cover_search_google), "https://www.google.com/imghp")

        //сайты бесплатных картинок: с подставленным запросом, а без названия - главная страница
        addChip(group, "На Unsplash",
            if (hasQuery) "https://unsplash.com/s/photos/$q" else "https://unsplash.com/")
        addChip(group, "На Magnific", "https://www.magnific.com/ru")
        addChip(group, "На Flickr",
            if (hasQuery) "https://www.flickr.com/search/?text=$q" else "https://www.flickr.com/")
        addChip(group, "На Pexels",
            if (hasQuery) "https://www.pexels.com/ru-ru/search/$q/" else "https://www.pexels.com/ru-ru/")
        addChip(group, "На Pixabay",
            if (hasQuery) "https://pixabay.com/ru/images/search/$q/" else "https://pixabay.com/ru/")
    }

    private fun addChip(group: ChipGroup, text: String, url: String) {
        val chip = Chip(group.context).apply {
            this.text = text
            isCheckable = false
            isClickable = true
            setOnClickListener { open(context, url) }
        }
        group.addView(chip, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.cover_search_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
