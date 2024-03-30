package us.shandian.giga.get

import org.schabi.newpipe.streams.io.StoredFileHelper
import java.io.Serializable
import java.util.Calendar

abstract class Mission() : Serializable {
    /**
     * Source url of the resource
     */
    var source: String? = null

    /**
     * Length of the current resource
     */
    var length: Long = 0

    /**
     * creation timestamp (and maybe unique identifier)
     */
    var timestamp: Long = 0
    fun getTimestamp(): Long {
        return timestamp
    }

    /**
     * pre-defined content type
     */
    var kind: Char = 0.toChar()

    /**
     * The downloaded file
     */
    var storage: StoredFileHelper? = null

    /**
     * Delete the downloaded file
     *
     * @return `true] if and only if the file is successfully deleted, otherwise, { false}`
     */
    open fun delete(): Boolean {
        if (storage != null) return storage!!.delete()
        return true
    }

    /**
     * Indicate if this mission is deleted whatever is stored
     */
    @Transient
    var deleted: Boolean = false
    public override fun toString(): String {
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeInMillis(timestamp)
        return "[" + calendar.getTime().toString() + "] " + (if (storage!!.isInvalid()) storage!!.getName() else storage!!.getUri())
    }

    companion object {
        private val serialVersionUID: Long = 1L // last bump: 27 march 2019
    }
}
