package com.example.android.architecture.blueprints.todoapp.util

import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}

inline fun <reified R : Any> Observable<*>.ofType(): Observable<R> = ofType(R::class.java)