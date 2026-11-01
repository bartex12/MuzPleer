package com.example.muzpleer.setting

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.fragment.app.Fragment
import com.example.muzpleer.R
import com.example.muzpleer.util.SleepTimer

/** Диалоги таймера остановки воспроизведения: выбор готового времени и выбор своего (часы и минуты). */
object SleepTimerDialog {

    private fun newDialog(context: Context, layout: Int): Dialog = Dialog(context).apply {
        setContentView(layout)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun show(fragment: Fragment) {
        val context = fragment.requireContext()
        val dialog = newDialog(context, R.layout.dialog_sleep_timer)
        val tvRemaining = dialog.findViewById<TextView>(R.id.tvTimerRemaining)
        val tvEnds = dialog.findViewById<TextView>(R.id.tvTimerEnds)
        val btnCancel = dialog.findViewById<android.view.View>(R.id.btnTimerCancel)

        //пока таймер идёт: остаток обновляется раз в секунду, появляется строка «Отменить таймер»
        val observer = Observer<Long?> { left ->
            val active = left != null
            tvRemaining.visibility = if (active) android.view.View.VISIBLE else android.view.View.GONE
            tvEnds.visibility = if (active) android.view.View.VISIBLE else android.view.View.GONE
            btnCancel.visibility = if (active) android.view.View.VISIBLE else android.view.View.GONE
            if (left != null) {
                tvRemaining.text = context.getString(R.string.sleep_timer_left, SleepTimer.formatRemaining(left))
                SleepTimer.endWall?.let {
                    tvEnds.text = context.getString(R.string.sleep_timer_stops_at, SleepTimer.formatEnd(it))
                }
            }
        }
        SleepTimer.remaining.observe(fragment.viewLifecycleOwner, observer)
        dialog.setOnDismissListener { SleepTimer.remaining.removeObserver(observer) }

        btnCancel.setOnClickListener {
            SleepTimer.cancel(context)
            Toast.makeText(context, R.string.sleep_timer_cancelled, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        for ((id, minutes) in listOf(R.id.btnTimer15 to 15, R.id.btnTimer30 to 30, R.id.btnTimer45 to 45, R.id.btnTimer60 to 60)) {
            dialog.findViewById<android.view.View>(id).setOnClickListener {
                SleepTimer.start(context, minutes * 60_000L)
                Toast.makeText(context, context.getString(R.string.sleep_timer_started, minutes), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.findViewById<android.view.View>(R.id.btnTimerCustom).setOnClickListener {
            dialog.dismiss()
            showCustom(context)
        }
        dialog.show()
    }

    private fun showCustom(context: Context) {
        val dialog = newDialog(context, R.layout.dialog_sleep_timer_custom)
        val hours = dialog.findViewById<NumberPicker>(R.id.npHours).apply { minValue = 0; maxValue = 23; value = 0; wrapSelectorWheel = true }
        val minutes = dialog.findViewById<NumberPicker>(R.id.npMinutes).apply { minValue = 0; maxValue = 59; value = 30; wrapSelectorWheel = true }
        dialog.findViewById<android.view.View>(R.id.btnTimerCustomCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<android.view.View>(R.id.btnTimerCustomApply).setOnClickListener {
            val total = (hours.value * 60L + minutes.value) * 60_000L
            if (total <= 0L) {
                Toast.makeText(context, R.string.sleep_timer_zero, Toast.LENGTH_SHORT).show()
            } else {
                //запускаем заново, даже если таймер уже шёл
                SleepTimer.start(context, total)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
