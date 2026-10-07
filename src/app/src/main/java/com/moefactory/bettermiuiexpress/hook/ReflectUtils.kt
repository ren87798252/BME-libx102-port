package com.moefactory.bettermiuiexpress.hook

import java.lang.reflect.Method

/**
 * Small reflection helpers shared by the hookers.
 *
 * The previous implementation relied on KavaRef (`resolve()`, `firstMethod {}`).
 * The modern API hooks plain [java.lang.reflect.Executable] instances, so the
 * lookup is done with plain reflection.
 */

/** Loads [name] without initialising it, or returns null when absent. */
internal fun loadClassOrNull(name: String, classLoader: ClassLoader): Class<*>? =
    runCatching { Class.forName(name, false, classLoader) }.getOrNull()

/**
 * Finds a method by exact parameter types, searching declared methods first and
 * then inherited ones.
 */
internal fun Class<*>.findMethodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
    val declared = declaredMethods.firstOrNull {
        it.name == name && it.parameterTypes.contentEquals(parameterTypes)
    }
    if (declared != null) return declared
    return methods.firstOrNull {
        it.name == name && it.parameterTypes.contentEquals(parameterTypes)
    }
}

/**
 * Finds a method by name and arity, accepting any parameter types that are
 * assignable from [assignableFrom] at the given position. Used for methods whose
 * third parameter is a subclass of the type the caller knows about.
 */
internal fun Class<*>.findMethodByShapeOrNull(
    name: String,
    arity: Int,
    assignableFrom: Class<*>,
    atIndex: Int
): Method? = declaredMethods.firstOrNull { candidate ->
    candidate.name == name &&
        candidate.parameterTypes.size == arity &&
        candidate.parameterTypes[atIndex].isAssignableFrom(assignableFrom)
}
