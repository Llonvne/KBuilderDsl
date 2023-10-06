package cn.llonvne

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class KotlinPoetResolver(
    private val environment: SymbolProcessorEnvironment
) {
    private val packageName = "cn.llonvne"

    fun fromClassname(name: String): ClassName = ClassName(packageName, name)

    private val file = FileSpec.builder(packageName, "Builders")

    fun registerType(typeSpec: TypeSpec) {
        file.addType(typeSpec)
    }

    fun registerFun(funSpec: FunSpec) {
        file.addFunction(funSpec)
    }

    fun resolve() {
        file.build().writeTo(environment.codeGenerator, Dependencies(false))
    }
}