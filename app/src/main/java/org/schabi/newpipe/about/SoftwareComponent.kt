package org.schabi.newpipe.about

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
class SoftwareComponent
@JvmOverloads
constructor(
    val name: String,
    val years: String,
    val copyrightOwner: String,
    val link: String,
    val license: License,
    val version: String? = null
) : Parcelable, Serializable
