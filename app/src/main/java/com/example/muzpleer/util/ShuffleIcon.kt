package com.example.muzpleer.util

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import com.example.muzpleer.R

/** Иконка «Случайный порядок»: в случайном режиме красится акцентом темы, в обычном - белая. */
fun MenuItem.applyShuffleIcon(context: Context, isShuffle: Boolean) {
    if (isShuffle) {
        setIcon(R.drawable.baseline_shuffle_24)
        icon?.mutate()?.setTint(context.accentColor())
    } else {
        setIcon(R.drawable.icons8_data_white_24)
    }
}

/** Иконка кнопки "Случайный порядок" в тулбаре должна показывать реальный порядок воспроизведения. */
fun Menu.syncShuffleIcon(context: Context, isShuffle: Boolean) {
    findItem(R.id.action_shuffle)?.applyShuffleIcon(context, isShuffle)
}
