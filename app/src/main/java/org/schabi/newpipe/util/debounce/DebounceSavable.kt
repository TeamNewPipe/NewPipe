package org.schabi.newpipe.util.debounce

import org.schabi.newpipe.error.ErrorInfo

interface DebounceSavable {
    /**
     * Execute operations to save the data. <br></br>
     * Must set [DebounceSaver.setIsModified] false in this method manually
     * after the data has been saved.
     */
    fun saveImmediate()
    fun showError(errorInfo: ErrorInfo?)
}
