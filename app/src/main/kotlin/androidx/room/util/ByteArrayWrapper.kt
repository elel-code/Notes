package androidx.room.util

class ByteArrayWrapper(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        return other is ByteArrayWrapper && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}
