package com.r3.logicapps.servicebus

import rx.Observable

/**
 * Simple interface to represent start/stop service lifecycle. It provides notification mechanism for service state change
 */
interface ServiceState {

    val active: Boolean

    val change: Observable<Boolean>

    fun start()

    fun stop()

}