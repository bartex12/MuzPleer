package com.example.muzpleer.util

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.muzpleer.R
import com.google.android.material.textfield.TextInputEditText

/** Диалог «Новый плейлист»: после нажатия «Создать» с непустым названием вызывает [onCreate]. */
fun showCreatePlaylistDialog(context: Context, onCreate: (name: String) -> Unit) {
    val dialog = Dialog(context).apply {
        setContentView(R.layout.dialog_create_playlist)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    val editText = dialog.findViewById<TextInputEditText>(R.id.etPlaylistName)
    val charCount = dialog.findViewById<TextView>(R.id.tvCharCount)
    val btnCreate = dialog.findViewById<Button>(R.id.btnCreate)
    val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

    editText?.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { charCount?.text = "${s?.length ?: 0}/50" }
    })
    btnCreate.setOnClickListener {
        val name = editText.text.toString().trim()
        if (name.isNotEmpty()) {
            dialog.dismiss()
            onCreate(name)
        } else {
            editText.error = "Введите название плейлиста"
        }
    }
    btnCancel?.setOnClickListener { dialog.dismiss() }
    editText?.requestFocus()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
}
