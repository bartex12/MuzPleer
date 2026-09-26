package com.example.muzpleer.ui.local

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.res.colorResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.example.muzpleer.MainActivity
import com.example.muzpleer.R
import com.example.muzpleer.databinding.FragmentTabslocalBinding
import com.example.muzpleer.ui.local.adapters.ViewPageAdapterLocal
import com.example.muzpleer.ui.local.viewmodel.SharedViewModel
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class TabLocalFragment: Fragment()  {
    companion object{
        const val TAG = "33333"
    }
    private var _binding: FragmentTabslocalBinding? = null
    private val binding get() = _binding!!

    lateinit var viewPager: ViewPager
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: ViewPageAdapterLocal
    private val viewModel: SharedViewModel by activityViewModel()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabslocalBinding.inflate(inflater, container, false )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "#TabLocalFragment onViewCreated:  ")
        initViews()
        initPageAdapter()
        //устанавливаем текущую вкладку - берём из преференсис
        viewPager.currentItem  =  viewModel.getTabsLocalPosition()

        // Устанавливаем слушатель на TabLayout
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val title = tab?.text?.toString() ?: ""
                Log.d(TAG, "#!# TabLocalFragment onViewCreated onTabSelected title = $title  ")
                (requireActivity() as? MainActivity)?.updateToolbarTitle(title)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Не используем
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Не используем
            }
        })
    }

    override fun onPause() {
        super.onPause()
        //запоминаем текущую вкладку
        viewModel.saveTabsLocalPosition(viewPager.currentItem)
    }

    private fun initViews() {
        viewPager = binding.viewPagerLocal
        tabLayout = binding.tabLayoutLocal
    }

    private fun initPageAdapter() {
        adapter =  ViewPageAdapterLocal(requireActivity(), childFragmentManager, viewPager)
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)

        val normalColor = Color.WHITE
        val selectedColor = ContextCompat.getColor(binding.root.context, R.color.colorSecondaryBlue)
        // val selectedColor =  Color.BLUE
        //устанавливаем цвет текста белый а при выделении - colorSecondaryBlue
        tabLayout.setTabTextColors(normalColor,  selectedColor)
        tabLayout.setSelectedTabIndicatorColor(selectedColor)
    }
}