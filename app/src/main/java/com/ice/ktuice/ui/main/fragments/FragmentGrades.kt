package com.ice.ktuice.ui.main.fragments

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ice.ktuice.R
import com.ice.ktuice.al.logger.IceLog
import com.ice.ktuice.al.logger.info
import com.ice.ktuice.al.notifications.SyncJob
import com.ice.ktuice.al.notifications.SyncJobWorker
import com.ice.ktuice.al.services.yearGradesService.YearGradesService
import com.ice.ktuice.repositories.prefrenceRepository.PreferenceRepository
import com.ice.ktuice.ui.main.components.GradeTable
import com.ice.ktuice.viewModels.gradesFragment.GradesFragmentViewModel
import kotlinx.android.synthetic.main.fragment_grades.*
import org.jetbrains.anko.doAsync
import org.koin.android.ext.android.inject
import org.koin.standalone.KoinComponent
import java.util.concurrent.TimeUnit


/**
 * Created by Andrius on 2/24/2018.
 * The main fragment of the application:
 * displays a Grade Table component and lets the student log out
 */
class FragmentGrades: Fragment(), KoinComponent, IceLog {

    private val yearGradesService: YearGradesService by inject()
    private val viewModel: GradesFragmentViewModel by inject()
    private val preferenceRepository: PreferenceRepository by inject()

    private lateinit var gradeTableView: GradeTable
    private val syncJob = SyncJob()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info("Creating Grades fragment!")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_grades, container, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gradeTableView = GradeTable(this.activity!!)
        grade_table_view_container.addView(gradeTableView)

        viewModel.grades.observe(this, Observer {
            if(it != null) {
                this.activity?.runOnUiThread {
                    // TODO reduce the view-model to only contain a key, or add PDO's
                    // realm can not pass objects between threads..
                    // so we re-fetch the grades here
                    val grades = yearGradesService.getYearGradesListFromDB()
                    if(grades != null) {
                        gradeTableView.updateGradeTable(grades, viewModel.selectedSemesterNumber.value, viewModel.selectedYear.value)
                    }
                }
            }
        })

        viewModel.loginModel.observe(this, Observer {  loginModel->
            this.activity?.runOnUiThread {
                loginModel!!
                info_student_code.text = loginModel.studentId
                info_student_name.text = loginModel.studentName
            }
        })

        doAsync {

            logout_btn.setOnClickListener{
                activity?.runOnUiThread {
                    logout()
                }
            }

            test_button.setOnClickListener{
                scheduleJob(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        scheduleJob(false)
        // shedule the notifications to be polled for  whenever the application is paused / stopped
    }

    private fun logout(){
        viewModel.dispose()
        viewModel.logoutCurrentUser(this.activity)
    }

    private fun scheduleJob(instant: Boolean){
        val wm = WorkManager.getInstance()
        val notificationFlag = if(instant) 0 else 1
        val dataToWorker = Data.Builder().putInt(resources.getString(R.string.notification_enabled_flag), notificationFlag).build()
        preferenceRepository.setValue(R.string.grade_scraper_retries, "0")

        if(!instant){
            val notificationWorkTag = resources.getString(R.string.notification_work_tag)

            val periodicSyncWork = PeriodicWorkRequestBuilder<SyncJobWorker>(3, TimeUnit.HOURS)
                    .setInputData(dataToWorker)
                    .addTag(notificationWorkTag)
                    .build()
            wm.enqueueUniquePeriodicWork(resources.getString(R.string.notification_work_name), ExistingPeriodicWorkPolicy.KEEP, periodicSyncWork)
            //TODO move period time to configuration, not inline
        }
        else{
            doAsync {
                syncJob.sync(0)
            }
        }
    }

}