/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.tasks

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.CheckBox
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.ScrollChildSwipeRefreshLayout
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.navigation.Navigator
import com.example.android.architecture.blueprints.todoapp.util.bindView
import com.example.android.architecture.blueprints.todoapp.util.obtainViewModel
import com.example.android.architecture.blueprints.todoapp.util.ofType
import com.example.android.architecture.blueprints.todoapp.util.plusAssign
import com.jakewharton.rxbinding2.support.v4.widget.refreshes
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.checkedChanges
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.disposables.CompositeDisposable
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Display a grid of [Task]s. User can choose to view all, active or completed tasks.
 */
class TasksFragment : Fragment() {
    private val swipeToRefreshLayout: ScrollChildSwipeRefreshLayout by bindView(R.id.refresh_layout)
    private val tasksList: RecyclerView by bindView(R.id.tasks_list)
    private val fab: FloatingActionButton by bindView(R.id.fab_add_task)
    private val viewModel: TasksViewModel by lazy(NONE) { obtainViewModel(TasksViewModel::class.java) }
    private val taskClickRelay = PublishRelay.create<String>()
    private val taskCompleteRelay = PublishRelay.create<Pair<String, Boolean>>()
    private val menuClickRelay = PublishRelay.create<Int>()

    private val disposables = CompositeDisposable()
    private val listAdapter = TasksAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.tasks_frag, container, false)
    }

    override fun onResume() {
        super.onResume()

        // Setup events, actions, results, states
        val taskFilters: Maybe<TasksEvent> = Maybe.create { emitter ->
            PopupMenu(context, activity.findViewById<View>(R.id.menu_filter)).run {
                menuInflater.inflate(R.menu.filter_tasks, menu)
                setOnMenuItemClickListener {
                    val filterType = when (it.itemId) {
                        R.id.active -> TasksFilterType.ACTIVE_TASKS
                        R.id.completed -> TasksFilterType.COMPLETED_TASKS
                        else -> TasksFilterType.ALL_TASKS
                    }
                    emitter.onSuccess(TasksEvent.LoadTasks(filterType))
                    return@setOnMenuItemClickListener true
                }
                show()
            }
        }

        val events = Observable.mergeArray(
                fab.clicks().map { TasksEvent.CreateTask },
                taskClickRelay.map { TasksEvent.ViewTask(it) },
                taskCompleteRelay.map { (taskId, toComplete) ->
                    if (toComplete) TasksEvent.CompleteTask(taskId)
                    else TasksEvent.UncompleteTask(taskId)
                },
                swipeToRefreshLayout.refreshes().map { TasksEvent.LoadTasks(TasksFilterType.ALL_TASKS) },
                menuClickRelay.flatMap { itemId ->
                    when (itemId) {
                        R.id.menu_refresh -> Observable.just(TasksEvent.LoadTasks(TasksFilterType.ALL_TASKS))
                        R.id.menu_clear -> Observable.just(TasksEvent.ClearCompleteTasks)
                        R.id.menu_filter -> taskFilters.toObservable()
                        else -> throw IllegalStateException("Invalid menu item clicked")
                    }
                })

        disposables += events.ofType<TasksEvent.CreateTask>()
                .subscribe { Navigator.navigateToNewTask(activity) }

        disposables += events.ofType<TasksEvent.ViewTask>()
                .subscribe { Navigator.navigateToTaskDetails(activity, it.taskId) }

        disposables += events.filter { it !is TasksEvent.CreateTask && it !is TasksEvent.ViewTask }
                .compose(tasksEventsToActions)
                .subscribe(viewModel.actions)

        disposables += viewModel.results
                .compose(tasksResultsToStates)
                .subscribe { render(it) }
    }

    private fun render(state: TasksUiState) {
        when (state) {
            is TasksUiState.TasksList -> {
                swipeToRefreshLayout.isRefreshing = false
                listAdapter.setData(state.tasks)
            }
            is TasksUiState.InProgress -> swipeToRefreshLayout.isRefreshing = true
            is TasksUiState.Failure -> {
                // TODO show error
                swipeToRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // TODO check if needed
        viewModel.handleActivityResult(requestCode, resultCode)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_clear || id == R.id.menu_filter || id == R.id.menu_refresh) {
            menuClickRelay.accept(item.itemId)
            return true
        } else {
            return false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tasks_fragment_menu, menu)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        swipeToRefreshLayout.run {
            setColorSchemeColors(ContextCompat.getColor(activity, R.color.colorPrimary),
                                 ContextCompat.getColor(activity, R.color.colorAccent),
                                 ContextCompat.getColor(activity, R.color.colorPrimaryDark))
            // Set the scrolling view in the custom SwipeRefreshLayout.
            scrollUpChild = tasksList
        }
        setHasOptionsMenu(true)
    }

    companion object {
        fun newInstance() = TasksFragment()
    }

    private val tasksEventsToActions: ObservableTransformer<TasksEvent, TasksAction> =
            ObservableTransformer { events ->
                events.map { event ->
                    when (event) {
                        is TasksEvent.LoadTasks -> TasksAction.LoadTasks(event.filterType, true)
                        is TasksEvent.ClearCompleteTasks -> TasksAction.ClearCompletedTasks
                        is TasksEvent.CompleteTask -> TasksAction.CompleteTask(event.taskId)
                        is TasksEvent.UncompleteTask -> TasksAction.UncompleteTask(event.taskId)
                        else -> throw IllegalStateException("Illegal event received")
                    }
                }
            }

    private val tasksResultsToStates: ObservableTransformer<TasksResult, TasksUiState> =
            ObservableTransformer { results ->
                results.map { result ->
                    when (result) {
                        is TasksResult.LoadTasksSuccess -> TasksUiState.TasksList(result.tasks)
                        is TasksResult.InProgress -> TasksUiState.InProgress
                        is TasksResult.Failure -> TasksUiState.Failure
                    }
                }
            }

    private inner class TasksAdapter : RecyclerView.Adapter<TaskViewHolder>() {
        private var tasks: List<Task> = listOf()

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            holder.setData(tasks[position])
        }

        override fun getItemCount() = tasks.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.task_item, parent, false)
            return TaskViewHolder(itemView)
        }

        fun setData(tasks: List<Task>) {
            this.tasks = tasks
            notifyDataSetChanged()
        }
    }

    private inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var task: Task? = null

        private val checkBox: CheckBox by bindView(R.id.complete)
        private val title: CheckBox by bindView(R.id.title)

        init {
            checkBox.checkedChanges().map { checked -> task!!.id to checked }.subscribe(taskCompleteRelay)
            itemView.clicks().map { task!!.id }.subscribe(taskClickRelay)
        }

        fun setData(task: Task) {
            checkBox.isChecked = task.isCompleted
            title.text = task.titleForList
        }
    }
}

sealed class TasksEvent {
    object CreateTask : TasksEvent()
    object ClearCompleteTasks : TasksEvent()
    data class LoadTasks(val filterType: TasksFilterType) : TasksEvent()
    data class ViewTask(val taskId: String) : TasksEvent()
    data class CompleteTask(val taskId: String) : TasksEvent()
    data class UncompleteTask(val taskId: String) : TasksEvent()
}

sealed class TasksUiState {
    data class TasksList(val tasks: List<Task>) : TasksUiState()
    object InProgress : TasksUiState()
    object Failure : TasksUiState()
}
