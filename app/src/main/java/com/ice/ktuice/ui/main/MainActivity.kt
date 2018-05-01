package com.ice.ktuice.ui.main

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.ice.ktuice.DAL.repositories.prefrenceRepository.PreferenceRepository
import com.ice.ktuice.R
import com.ice.ktuice.al.services.userService.UserService
import com.ice.ktuice.ui.adapters.FragmentAdapter
import com.ice.ktuice.ui.login.LoginActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.koin.android.ext.android.inject


class MainActivity : AppCompatActivity() {
    private val userService: UserService by inject()
    private val preferenceRepository:PreferenceRepository by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try{
            /**
             * If the user is not yet logged in,
             * this will throw a null reference exception
             */
            userService.getLoginForCurrentUser()
            val preferredTab = preferenceRepository.getValue(R.string.currently_selected_tab_id)
            setContentView(R.layout.activity_main)
            main_activity_view_pager.adapter = FragmentAdapter(this.supportFragmentManager, this)
            try {
                val preferredTabInt = preferredTab.toInt()
                main_activity_view_pager.currentItem = preferredTabInt
            }catch (e: Exception){}

        }catch (e: NullPointerException){
            launchLoginActivity()
        }
    }

    private fun launchLoginActivity(){
        runOnUiThread{
            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            this.finish()
        }
    }
}
