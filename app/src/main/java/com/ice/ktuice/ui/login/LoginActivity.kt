package com.ice.ktuice.ui.login

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.ice.ktuice.repositories.loginRepository.LoginRepository
import com.ice.ktuice.repositories.prefrenceRepository.PreferenceRepository
import com.ice.ktuice.R
import com.ice.ktuice.ui.main.MainActivity
import com.ice.ktuice.models.LoginModel
import com.ice.ktuice.al.services.scrapers.base.ScraperService
import kotlinx.android.synthetic.main.activity_login.*
import org.jetbrains.anko.*
import org.koin.android.ext.android.inject
import java.util.concurrent.Future

/**
 * Created by Andrius on 1/24/2018.
 * TODO refactor the login system to a more robust system
 */
class LoginActivity: AppCompatActivity(), AnkoLogger {

    private val loginRepository: LoginRepository by inject()
    private val preferenceRepository: PreferenceRepository by inject()
    private val scraperService: ScraperService by inject()

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        info("Creating login activity!")
        val loggedInUserCode = preferenceRepository.getValue(R.string.logged_in_user_code)
        if(loggedInUserCode.isNotBlank()){
            val login = loginRepository.getByStudCode(loggedInUserCode)
            if(login!= null){
                info("Launching with logged in user code:"+login.studentId)
                launchMainActivity()
            }
        }
        setContentView(R.layout.activity_login)

        login_submit_button.setOnClickListener{
            val username = login_username_field.text.toString()
            val password = login_password_field.text.toString()
            val loginFuture= loginRequest(username, password)

            doAsync {
                val loginModel = loginFuture.get()
                if (loginModel == null) {
                    setErrorDisplay(resources.getString(R.string.failed_login), true)
                    setLoadingVisible(false)
                } else {
                    activityUiThreadWithContext {
                        saveLoginToRealm(loginModel)
                        preferenceRepository.setValue(R.string.logged_in_user_code, loginModel.studentId)
                        info("login saved to database, launching main activity!")
                        launchMainActivity()
                    }
                }
            }
        }
    }

    private fun launchMainActivity(){
        runOnUiThread{
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginRequest(username:String, password:String): Future<LoginModel?>{
        return doAsyncResult(
                {
                    info(it)
                    info(it.getStackTraceString())
                    runOnUiThread{
                        setErrorDisplay(it.toString(), true)
                        setLoadingVisible(false)
                    }
                },
                {
                    var loginModel: LoginModel? = null
                    try {
                        setLoadingVisible(true)
                        val loginResponse = scraperService.login(username, password)
                        if(loginResponse.loginModel != null) {
                            setLoadingVisible(false)
                            loginModel = loginResponse.loginModel
                            info("Login successful! " + loginModel.studentName)
                        }
                    }catch (e: Exception){
                        info("Exception while making the login requests!:$e")
                        setErrorDisplay(e.toString(), true)
                        info(e.getStackTraceString())
                        setLoadingVisible(false)
                    }
                    return@doAsyncResult loginModel
                })
    }


    private fun saveLoginToRealm(loginModel: LoginModel){
        loginRepository.createOrUpdate(loginModel)
    }

    private fun setLoadingVisible(visible:Boolean){
        runOnUiThread {
            if(visible){
                login_submit_button.isEnabled = false
                login_spinner.visibility = View.VISIBLE
            }else{
                login_submit_button.isEnabled = true
                login_spinner.visibility = View.GONE
            }
        }
    }

    private fun setErrorDisplay(errorText:String, visible:Boolean){
        runOnUiThread {
            login_error_text.text = errorText
            if(visible){
                login_error_container.visibility = View.VISIBLE
            }else{
                login_error_container.visibility = View.GONE
            }
        }
    }
}