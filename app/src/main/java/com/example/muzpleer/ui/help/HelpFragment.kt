package com.example.muzpleer.ui.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentHelpBinding
import com.example.muzpleer.databinding.ItemHelpSectionBinding

/**
 * Экран "Справка": список разделов, каждый раскрывается нажатием на заголовок.
 * Заголовки и тексты лежат в strings.xml (help_titles / help_texts), порядок в массивах совпадает.
 */
class HelpFragment : Fragment() {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titles = resources.getStringArray(R.array.help_titles)
        val texts = resources.getStringArray(R.array.help_texts)
        //ключ состояния: какие разделы раскрыты (переживает поворот экрана)
        val opened = savedInstanceState?.getBooleanArray(KEY_OPENED) ?: BooleanArray(titles.size)

        titles.indices.forEach { i ->
            val item = ItemHelpSectionBinding.inflate(layoutInflater, binding.helpContainer, false)
            item.helpTitle.text = titles[i]
            item.helpText.text = texts.getOrElse(i) { "" }
            setOpened(item.helpTitle, item.helpText, opened.getOrElse(i) { false })
            item.helpTitle.setOnClickListener {
                val show = item.helpText.visibility != View.VISIBLE
                setOpened(item.helpTitle, item.helpText, show)
            }
            binding.helpContainer.addView(item.root)
        }
    }

    private fun setOpened(title: TextView, text: View, open: Boolean) {
        text.visibility = if (open) View.VISIBLE else View.GONE
        title.setCompoundDrawablesWithIntrinsicBounds(
            0, 0, if (open) R.drawable.ic_help_arrow_up else R.drawable.ic_help_arrow_down, 0
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val container = _binding?.helpContainer ?: return
        outState.putBooleanArray(KEY_OPENED, BooleanArray(container.childCount) { i ->
            container.getChildAt(i).findViewById<View>(R.id.helpText).visibility == View.VISIBLE
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_OPENED = "help_opened"
    }
}
