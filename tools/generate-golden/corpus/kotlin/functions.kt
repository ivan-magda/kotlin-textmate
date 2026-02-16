package example

import kotlin.collections.List

fun main(args: Array<String>) {
    val message = "Hello, World!"
    println(message)
}

fun <T : Comparable<T>> sort(list: List<T>): List<T> {
    return list.sorted()
}

class Calculator {
    fun add(a: Int, b: Int): Int = a + b

    companion object {
        const val PI = 3.14159
    }
}

annotation class MyAnnotation

@MyAnnotation
fun annotated() {
    val x = when (true) {
        true -> 1
        false -> 0
    }
}
