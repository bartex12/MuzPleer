package com.example.muzpleer.util

import android.view.Menu
import com.example.muzpleer.R

/** Иконка кнопки "Случайный порядок" в тулбаре должна показывать реальный порядок воспроизведения. */
fun Menu.syncShuffleIcon(isShuffle: Boolean) {
    findItem(R.id.action_shuffle)?.setIcon(
        if (isShuffle) R.drawable.baseline_shuffle_24 else R.drawable.icons8_data_white_24
    )
}
