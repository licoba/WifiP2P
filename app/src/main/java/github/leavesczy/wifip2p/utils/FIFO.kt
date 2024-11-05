package github.leavesczy.wifip2p.utils

import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock


class FIFOBytes(private val capacity: Int) {
    private val queue = LinkedBlockingQueue<Byte>(capacity)

    fun push(data: ByteArray) {
        if (data.size > capacity) {
            Log.e("Fifo", "Data size exceeds FIFOBytes capacity")
            return
        }

        for (byte in data) {
            queue.put(byte) // 阻塞直到有空间
        }
    }

    fun size(): Int {
        return queue.size
    }

    fun pop(pkgSize: Int): ByteArray {
        val result = ByteArray(pkgSize)
        for (i in 0 until pkgSize) {
            result[i] = queue.take() // 阻塞直到有数据可用
        }
        return result
    }

    fun clear() {
        queue.clear()
    }
}



class FIFOByteArray(private val capacity: Int) {
    private val queue = ArrayBlockingQueue<ByteArray>(capacity)

    fun push(byteArray: ByteArray) {
        try {
            queue.put(byteArray)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun pop(): ByteArray? {
        return try {
            queue.take()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            null
        }
    }
}