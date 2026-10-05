package com.explorer.fileexplorer.core.data

import android.util.Log
import com.explorer.fileexplorer.core.model.RepositoryError

class DiagnosticLog {
    fun log(
        error: RepositoryError,
        context: String? = null,
    ) {
        Log.w(
            "YFiles/Remote",
            buildString {
                append(error.provider)
                append('/')
                append(error.operation.name)
                append(": ")
                append(error.message)
                context?.takeIf { it.isNotBlank() }?.let {
                    append(" @ ")
                    append(it)
                }
            },
        )
    }
}
