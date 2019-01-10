package io.github.bartvhelvert.jagex.fs

interface Dictionary

abstract class DictionaryCompanion<D : Dictionary> {
    abstract val id: Int

    abstract fun load(cache: JagexCache): D
}