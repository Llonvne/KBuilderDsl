package cn.llonvne

import BuilderDsl
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

class KBuilderProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val kotlinPoetResolver = KotlinPoetResolver(environment)

    @Suppress("unchecked_cast")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classes = resolver.getSymbolsWithAnnotation<BuilderDsl>()
            .toList().filterDecision {
                acceptIf {
                    it is KSClassDeclaration
                            && it.classKind == ClassKind.CLASS
                            && !it.isAbstract()
                            && Modifier.DATA in it.modifiers
                }
                reject {
                    environment.logger.error("$it it not a data class")
                }
            } as List<KSClassDeclaration>

        classes.forEach {
            KBuilderDslResolver(it, environment, resolver, kotlinPoetResolver).resolve()
        }

        return emptyList()
    }

    override fun finish() {
        kotlinPoetResolver.resolve()
    }
}

inline fun <reified T> Resolver.getSymbolsWithAnnotation() = getSymbolsWithAnnotation(T::class.java.name)
