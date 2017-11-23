package com.example.android.architecture.blueprints.todoapp.navigation

import android.content.Intent
import android.support.v4.app.FragmentActivity
import com.example.android.architecture.blueprints.todoapp.addedittask.AddEditTaskActivity
import com.example.android.architecture.blueprints.todoapp.taskdetail.TaskDetailActivity

const val EXTRA_TASK_ID = "TASK_ID"

const val ADD_EDIT_TASK_REQUEST_CODE = 1

object Navigator {
    fun navigateToTaskDetails(activity: FragmentActivity, taskId: String) {
        val intent = Intent(activity, TaskDetailActivity::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
        }
        activity.startActivityForResult(intent, ADD_EDIT_TASK_REQUEST_CODE)
    }

    fun navigateToNewTask(activity: FragmentActivity) {
        val intent = Intent(activity, AddEditTaskActivity::class.java)
        activity.startActivityForResult(intent, ADD_EDIT_TASK_REQUEST_CODE)
    }
}