package us.shandian.giga.get

import java.io.Serializable
import java.util.*
import org.schabi.newpipe.streams.io.StoredFileHelper

abstract class Mission : Serializable {
    /**
     * Source url of the resource
     */
    @JvmField
    var source: String? = null

    /**
     * Length of the current resource
     */
    @JvmField
    var length: Long = 0

    /**
     * creation timestamp (and maybe unique identifier)
     */
    @get:JvmName("getTimestamp")
    @JvmField
    var timestamp: Long = 0

    /**
     * pre-defined content type
     */
    @JvmField
    var kind: Char = ' '

    /**
     * The downloaded file
     */
    @JvmField
    var storage: StoredFileHelper? = null

    @JvmField
    var title: String? = null

    @JvmField
    var uploader: String? = null

    @JvmField
    var thumbnailUrl: String? = null

    @JvmField
    var duration: Long = 0L

    /**
     * Delete the downloaded file
     *
     * @return `true` if and only if the file is successfully deleted, otherwise, `false`
     */
    open fun delete(): Boolean {
        return storage?.delete() ?: true
    }

    /**
     * Indicate if this mission is deleted whatever is stored
     */
    @Transient
    var deleted = false

    override fun toString(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return "[" + calendar.time.toString() + "] " + if (storage!!.isInvalid()) storage!!.getName() else storage!!.getUri()
    }

    companion object {
        private const val serialVersionUID = 1L // last bump: 27 march 2019
    }
}
