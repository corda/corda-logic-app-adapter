package com.r3.logicapps.workbench

open class IngressFormatException(
    override val message: String?,
    override val cause: Throwable?
) : IllegalArgumentException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String) : this(message, null)
    constructor(cause: Throwable) : this(cause.toString(), cause)
}

class CorrelatableIngressFormatException(
    override val message: String?,
    override val cause: Throwable?,
    val requestId: String
) : IngressFormatException(message, cause) {
    constructor(requestId: String) : this(null, null, requestId)
    constructor(message: String, requestId: String) : this(message, null, requestId)
    constructor(cause: Throwable, requestId: String) : this(cause.toString(), cause, requestId)
}
