package com.ice.ktuice.ui.main.components

import android.app.Fragment
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.ice.ktuice.DAL.repositories.prefrenceRepository.PreferenceRepository
import com.ice.ktuice.R
import com.ice.ktuice.al.GradeTable.GradeTableManager
import com.ice.ktuice.al.GradeTable.gradeTableModels.GradeTableModel
import com.ice.ktuice.al.GradeTable.gradeTableModels.SemesterAdapterItem
import com.ice.ktuice.al.GradeTable.yearGradesModelComparator.YearGradesModelComparator
import com.ice.ktuice.al.services.userService.UserService
import com.ice.ktuice.al.services.yearGradesService.YearGradesService
import com.ice.ktuice.models.YearGradesCollectionModel
import com.ice.ktuice.ui.adapters.SemesterSpinnerAdapter
import com.ice.ktuice.ui.main.dialogs.GradeTableCellDetailsDialog
import kotlinx.android.synthetic.main.grade_table_layout.*;
import org.jetbrains.anko.*
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject

/**
 * Created by Andrius on 1/29/2018.
 * The Grade table component contains a spinner for semester selection,
 * and then the grades in a table, that correspond to the selected semester.
 * TODO move all the application logic to GradeTableManager
 */
class GradeTable(
         c: Context,
         attrs: AttributeSet?
): LinearLayout(c, attrs), KoinComponent, AnkoLogger {

    private val yearGradesService: YearGradesService by inject()
    private val comparator: YearGradesModelComparator by inject()
    private val preferenceRepository: PreferenceRepository by inject()

    private var tableCellDetailsDialog: GradeTableCellDetailsDialog? = null
    private var tableModel: GradeTableModel? = null

    private val CELL_PADDING_H: Int = context.resources.getInteger(R.integer.grade_table_cell_padding_horizontal)
    private val CELL_PADDING_V: Int = context.resources.getInteger(R.integer.grade_table_cell_padding_vertical)
    private val CELL_TEXT_SIZE = context.resources.getInteger(R.integer.grade_table_cell_text_size).toFloat()


    private val updating_progress: LinearLayout
    private val grade_table_semester_spinner: Spinner
    private val grade_table_table_layout:TableLayout
    private val loading_overlay:LinearLayout


    private val tableManager = GradeTableManager()
    private var currentGrades: YearGradesCollectionModel? = null
    constructor(c:Context): this(c, null)

    /**
     * Displays or hides the simple loading overlay, that covers the whole view
     */
    private var isLoadingOverlayShown: Boolean
        get() = loading_overlay.isShown
        set(value){
            val newVisibility = if(value)View.VISIBLE
            else View.GONE
            loading_overlay.visibility = newVisibility
        }

    init {
        inflate(context, R.layout.grade_table_layout, this)

        updating_progress = this.findViewById(R.id.updating_progress)
        grade_table_semester_spinner = this.findViewById(R.id.grade_table_semester_spinner)
        grade_table_table_layout = this.findViewById(R.id.grade_table_table_layout)
        loading_overlay = this.findViewById(R.id.loading_overlay)

        isLoadingOverlayShown = true
//        gradesData.observe(parentFragment.activity, Observer {
//
//        })

//        val gradesSubject  = yearGradesService.getYearGradesList()
//        gradesSubject.subscribe{
//            context.runOnUiThread {
//                /**
//                 * This is the handler that updates a view, thus it has to run on the ui thread.
//                 */
//                info("Observing a grade table!")
//                val gradeTable = yearGradesService.getYearGradesListFromDB()!!
//                if(gradeTable.isEmpty()){
//                    /*
//                        On the initial load, the returned value is just a placeholder,
//                        here we subscribe to the whole realm to keep track of the freshly loaded
//                        grade table
//                     */
//                    isLoadingOverlayShown = true
//                }else{
//                    updateGradeTable(gradeTable)
//                }
//            }
//        }
    }

    fun updateGradeTable(grades: YearGradesCollectionModel){
        if(grades.yearList.size == 0){
            return@updateGradeTable
        }
        /**
         * Manage the loading states
         */
        isLoadingOverlayShown = false

        info("Observed grade table is updating: ${grades.isUpdating}")
        if(grades.isUpdating){
            updating_progress.visibility = View.VISIBLE
        }else{
            updating_progress.visibility = View.GONE
        }
        if(currentGrades != null && comparator.compare(currentGrades!!, grades).isEmpty()) {
            /**If there are no changes to the data,
             * Updating the view is pointless, so shortcircuit this
             */
            return@updateGradeTable
        }
        if(grade_table_semester_spinner.adapter == null){
            //construct table spinner on initial widget construction
            val semesterSpinnerItems = tableManager.constructSemesterAdapterSpinnerItemList(grades)
            setUpSemesterSpinner(semesterSpinnerItems)
            grade_table_semester_spinner.setSelection(semesterSpinnerItems.lastIndex, true)
        }
        val changedSemesterSpinnerItems = tableManager.constructSemesterAdapterSpinnerItemList(grades)
        val changedTableModel = tableManager.constructGradeTableModel(grades)

        setUpSemesterSpinner(changedSemesterSpinnerItems)
        try {
            val preferredSemesterStr = preferenceRepository.getValue(R.string.currently_selected_semester_id)
            info("Selecting the semester to be :$preferredSemesterStr")
            grade_table_semester_spinner.setSelection(changedSemesterSpinnerItems.indexOfFirst { it.semester.equals(preferredSemesterStr) })
        }catch(exception: IndexOutOfBoundsException){
            info("The current spinner selection is invalid!")
        }

        tableModel = changedTableModel

        currentGrades = grades
        //The set selection updates the grade table view
    }

    /**
     * Main view inflation and recycling in this function:
     * it creates a table view for the specified model and if provided, the selected semester
     */
    private fun createViewForModel(gradeTableModel: GradeTableModel, semesterAdapterItem: SemesterAdapterItem? = null){
        grade_table_table_layout.removeAllViews()
//        grade_table_headers.removeAllViews()

        if(semesterAdapterItem != null)gradeTableModel.selectSemester(semesterAdapterItem.semesterNumber)
        val rowModelList = gradeTableModel.getRows()
        val weekModelList = gradeTableModel.getTotalWeekList()

        //header generation
        val weekTableRow = TableRow(context)

        /**
         * Adding the "week" row header
         */
        val weekTitleContainer = LinearLayout(context)
        val weekTitle = TextView(context)
        weekTitle.setText(R.string.grade_table_week_header)
        weekTitleContainer.setPadding(CELL_PADDING_H,0,0,0)
        weekTitle.setBackgroundResource(R.drawable.grade_cell_background)
        weekTitleContainer.addView(weekTitle)
        weekTitle.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        weekTableRow.addView(weekTitleContainer)

        /**
         * Adding all available week numbers
         */
        weekModelList?.forEach {
            val weekText = TextView(context)
            weekText.text = it.week
            weekText.setBackgroundResource(R.drawable.grade_cell_background)
            weekTableRow.addView(weekText)
        }
        grade_table_table_layout.addView(weekTableRow, TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f))


        rowModelList?.forEach {
            val tableRow = TableRow(context)
            val moduleName = it.moduleModel.module_name
            val moduleNameText = TextView(context)
            moduleNameText.text = moduleName
            moduleNameText.setPadding(CELL_PADDING_H, CELL_PADDING_V, CELL_PADDING_V, CELL_PADDING_H)
            moduleNameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, CELL_TEXT_SIZE)

            tableRow.addView(moduleNameText)

            weekModelList?.forEach { weekModel ->
                val markCellText = TextView(context)
                markCellText.setPadding(CELL_PADDING_H, CELL_PADDING_V, CELL_PADDING_V, CELL_PADDING_H)
                markCellText.setTextSize(TypedValue.COMPLEX_UNIT_SP, CELL_TEXT_SIZE)
                val cellModel = it.getByWeekModel(weekModel)
                markCellText.text = cellModel?.getDisplayString() // default is empty cell
                markCellText.setOnClickListener{
                    markCellText.post {
                        tableCellDetailsDialog?.CellModel = cellModel
                        tableCellDetailsDialog?.show()
                    }
                }
                tableRow.addView(markCellText)
            }
            grade_table_table_layout.addView(tableRow, TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }

    }

    private fun setUpSemesterSpinner(itemList: List<SemesterAdapterItem>){
        val adapter = SemesterSpinnerAdapter(context, itemList)
        grade_table_semester_spinner.adapter = adapter
        grade_table_semester_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p0: AdapterView<*>?) {
                // this should never happen
            }

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val item = adapter.getItem(p2)
                preferenceRepository.setValue(R.string.currently_selected_semester_id, item.semester)
                info("Creating view for spinner item")
                createViewForModel(tableModel!!, item)
            }

        }
    }

}