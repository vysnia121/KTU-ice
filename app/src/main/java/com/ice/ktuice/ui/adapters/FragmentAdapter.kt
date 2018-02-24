package com.ice.ktuice.ui.adapters

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.ice.ktuice.ui.main.fragments.FragmentGrades
import com.ice.ktuice.ui.main.fragments.FragmentTimeTable

/**
 * Created by Andrius on 2/24/2018.
 */
class FragmentAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
    override fun getItem(position: Int): Fragment {
        val retFrag: Fragment
        if(position == 0) retFrag = FragmentGrades()
        else retFrag = FragmentTimeTable()
        return retFrag
    }

    override fun getCount() = 2

    private val titles = arrayListOf("Grades", "Time Table")
    override fun getPageTitle(position: Int) = titles[position]

}