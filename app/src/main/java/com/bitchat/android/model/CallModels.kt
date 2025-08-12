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
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Parcelize
data class VoiceStreamPacket(
    override val callID: CallID,
    val sequenceNumber: UInt,
    val audioData: ByteArray
) : CallPacket() {
    fun toBinaryPayload(): ByteArray {
        val callIDBytes = callID.toByteArray(Charsets.UTF_8)
        // 1 byte for callID length, callID bytes, 4 bytes for seq num, audio data
        val buffer = ByteBuffer.allocate(1 + callIDBytes.size + 4 + audioData.size).apply {
            order(ByteOrder.BIG_ENDIAN)
        }

        buffer.put(callIDBytes.size.toByte())
        buffer.put(callIDBytes)
        buffer.putInt(sequenceNumber.toInt())
        buffer.put(audioData)

        return buffer.array()
    }

    companion object {
        fun fromBinaryPayload(data: ByteArray): VoiceStreamPacket? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply {
                    order(ByteOrder.BIG_ENDIAN)
                }

                val callIDLength = buffer.get().toInt() and 0xFF
                val callIDBytes = ByteArray(callIDLength)
                buffer.get(callIDBytes)
                val callID = String(callIDBytes, Charsets.UTF_8)

                val sequenceNumber = buffer.int.toUInt()

                val audioData = ByteArray(buffer.remaining())
                buffer.get(audioData)

                VoiceStreamPacket(
                    callID = callID,
                    sequenceNumber = sequenceNumber,
                    audioData = audioData
                )
            } catch (e: Exception) {
                null
            }
        }
    }

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
