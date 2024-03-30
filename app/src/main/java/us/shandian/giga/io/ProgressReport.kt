package us.shandian.giga.io

open interface ProgressReport {
    /**
     * Report the size of the new file
     *
     * @param progress the new size
     */
    fun report(progress: Long)
}