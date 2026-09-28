package com.github.zarechenskiy.explicitboxing.ide

import com.github.zarechenskiy.explicitboxing.bytecode.CompiledClassesIndex
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class BoxingService {
    val index = CompiledClassesIndex()

    companion object {
        fun getInstance(): BoxingService = service()
    }
}
