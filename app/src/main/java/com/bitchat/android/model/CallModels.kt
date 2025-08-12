package com.bitchat.android.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A unique identifier for a call session.
 */
typealias CallID = String

/**
 * Base class for all call-related signaling packets.
 */
sealed class CallPacket : Parcelable {
    abstract val callID: CallID
}

@Parcelize
data class CallRequest(
    override val callID: CallID,
    val callerNickname: String,
    val calleePeerID: String
) : CallPacket()

@Parcelize
data class CallAccept(
    override val callID: CallID,
    val acceptorNickname: String
) : CallPacket()

@Parcelize
data class CallDecline(
    override val callID: CallID,
    val declinerNickname: String
) : CallPacket()

@Parcelize
data class CallEnd(
    override val callID: CallID
) : CallPacket()

/**
 * Represents a single packet of voice data in a call.
 */
@Parcelize
data class VoiceStreamPacket(
    override val callID: CallID,
    val sequenceNumber: UInt,
    val audioData: ByteArray
) : CallPacket() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoiceStreamPacket

        if (callID != other.callID) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!audioData.contentEquals(other.audioData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = callID.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + audioData.contentHashCode()
        return result
    }
}

/**
 * Represents the current state of a voice call.
 */
@Parcelize
data class CallState(
    val status: Status,
    val callID: CallID? = null,
    val remotePeerID: String? = null,
    val remoteNickname: String? = null
) : Parcelable {
    enum class Status {
        IDLE,
        OUTGOING,
        INCOMING,
        ACTIVE
    }
}
