package us.shandian.giga.io

fun interface ProgressReport {
    /**
     * Report the size of the new file
     *
     * @param progress the new size
     */
    fun report(progress: Long)
}
