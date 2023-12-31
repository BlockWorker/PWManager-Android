package de.blockworker.pwmanager

import java.security.MessageDigest
import java.util.Arrays
import java.util.Base64

object PasswordGenerator {

    fun generate(master: String, ident: String, iter: UInt, symbols: String, long: Boolean): String {
        val base = master + ident + iter.toString()
        val sha384Digest = MessageDigest.getInstance("SHA-384")
        val sha1Digest = MessageDigest.getInstance("SHA-1")

        val baseBytes = base.encodeToByteArray()
        sha384Digest.update(baseBytes)
        val sha384Bytes = sha384Digest.digest()

        val nHash = Arrays.copyOf(sha384Bytes, sha384Bytes.size)
        if (long) nHash[47] = (nHash[47].toUByte() % 3u).toByte()

        sha1Digest.update(nHash)
        val sha1Bytes = sha1Digest.digest()

        val pwHash = ByteArray(if (long) 12 else 6)
        val hashOffset = if (long) ((sha1Bytes[19].toUByte() % 4u) * 12u)
                            else ((sha1Bytes[19].toUByte() % 8u) * 6u)
        for (i in pwHash.indices) {
            pwHash[i] = sha384Bytes[hashOffset.toInt() + i]
        }

        var password = Base64.getEncoder().encodeToString(pwHash)
        val numsymbols = if (long) 8 else 4
        val numchars = if (long) 16 else 8
        for (i in 0 until numsymbols) {
            val place = (sha1Bytes[2 * i].toUByte() % (numchars + i).toUInt()).toInt()
            val symbol = symbols[(sha1Bytes[2 * i + 1].toUByte() % symbols.length.toUInt()).toInt()]
            password = insert(password, place, symbol)
        }

        sha384Digest.reset()
        sha1Digest.reset()
        sha384Bytes.fill(0)
        nHash.fill(0)
        sha1Bytes.fill(0)

        return password
    }

    private fun insert(str: String, pos: Int, char: Char): String {
        return if (pos > 0) {
            str.substring(0, pos) + char + str.substring(pos)
        } else {
            char + str
        }
    }

}