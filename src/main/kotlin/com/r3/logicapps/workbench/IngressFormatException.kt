package com.r3.logicapps.workbench

class IngressFormatException : IllegalArgumentException {
    constructor() : super()
    constructor(s: String) : super(s)
    constructor(s: String, cause: Throwable) : super(s, cause)
    constructor(cause: Throwable) : super(cause)
}