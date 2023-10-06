package cn.llonvne

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import java.util.*


class KBuilderDslResolver(
    private val target: KSClassDeclaration,
    private val environment: SymbolProcessorEnvironment,
    private val resolver: Resolver,
    private val kotlinPoetResolver: KotlinPoetResolver
) {
    private val classSimpleName = target.simpleName.asString()
    private fun getNotNullNoDefaultConstructInterfaceName(parameterName: String) =
        "$classSimpleName${parameterName}Builder"

    private val typeParameterResolver = target.typeParameters.toTypeParameterResolver()


    private fun parameters() = extractMemberDecls().toList()

    private fun buildNotNullNoDefaultBuilderType(cur: KSValueParameter, next: KSValueParameter? = null): TypeSpec {

        val parameterName = cur.name?.asString()!!

        val capitalizeParameterName =
            parameterName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val function = FunSpec.builder(parameterName).addParameter(
            ParameterSpec.builder(
                "builder", LambdaTypeName.get(
                    receiver = null,
                    parameters = emptyList(),
                    returnType = cur.type.resolve().toTypeName(typeParameterResolver)
                )
            ).build()
        ).addModifiers(KModifier.ABSTRACT)

        if (next == null) {
            function.returns(kotlinPoetResolver.fromClassname(classSimpleName + "Builder"))
        } else {
            function.returns(
                kotlinPoetResolver.fromClassname(
                    getNotNullNoDefaultConstructInterfaceName(next.name?.asString()?.capitalize()!!)
                )
            )
        }
        return TypeSpec.interfaceBuilder(getNotNullNoDefaultConstructInterfaceName(capitalizeParameterName))
            .addFunction(function.build()).build()
    }

    fun resolve() {
        val notNullNoDefaultParameters = parameters().filter { !it.type.resolve().isMarkedNullable && !it.hasDefault }
        val constructorInterfaces = notNullNoDefaultParameters.mapIndexed { index, ksValueParameter ->
            buildNotNullNoDefaultBuilderType(ksValueParameter, notNullNoDefaultParameters.getOrNull(index + 1))
        }.toList()
        constructorInterfaces.forEach { kotlinPoetResolver.registerType(it) }
        val generalBuilderType = TypeSpec.classBuilder("${classSimpleName}Builder")
            .buildPrimaryConstructor()
        constructorInterfaces.forEach { generalBuilderType.addSuperinterface(kotlinPoetResolver.fromClassname(it.name!!)) }
        generalBuilderType.implFuns()
        generalBuilderType.buildFucImpl()
        val general = generalBuilderType.build()
        buildExtFun(constructorInterfaces.firstOrNull(), general)
        kotlinPoetResolver.registerType(general)
    }

    private fun buildExtFun(type: TypeSpec?, generalBuilderType: TypeSpec) {

        kotlinPoetResolver.registerFun(
            FunSpec.builder("${classSimpleName.toLowerCase()}Builder")
                .also {
                    if (type != null) {
                        it.returns(kotlinPoetResolver.fromClassname(type.name!!))
                    } else {
                        it.returns(kotlinPoetResolver.fromClassname(generalBuilderType.name!!))
                    }
                }
                .addStatement("return ${kotlinPoetResolver.fromClassname(generalBuilderType.name!!)}()")
                .build()
        )
    }

    private fun TypeSpec.Builder.implFuns(): TypeSpec.Builder {
        parameters().forEach { parameter ->
            addFunction(
                FunSpec.builder(parameter.name?.asString()!!)
                    .also {
                        if (!parameter.type.resolve().isMarkedNullable && !parameter.hasDefault) {
                            it.addModifiers(KModifier.OVERRIDE)
                        }
                    }
                    .returns(kotlinPoetResolver.fromClassname(classSimpleName + "Builder"))
                    .addParameter(
                        ParameterSpec.builder(
                            "builder", LambdaTypeName.get(
                                receiver = null,
                                parameters = emptyList(),
                                returnType = parameter.type.resolve().toTypeName(typeParameterResolver)
                            )
                        ).build()
                    )
                    .addStatement("%L = builder()", parameter.name?.asString()!!)
                    .addStatement("return this")
                    .build()
            )
        }

        return this
    }

    private fun extractMemberDecls(): List<KSValueParameter> {
        return target.primaryConstructor!!.parameters.toList()
    }

    private fun TypeSpec.Builder.buildPrimaryConstructor(): TypeSpec.Builder {
        primaryConstructor(
            FunSpec.constructorBuilder()
                .also { constructor ->
                    parameters().forEach {
                        // 添加 val 关键字到参数声明中
                        val parameterSpec = ParameterSpec.builder(
                            it.name?.asString()!!, it.type.toTypeName(typeParameterResolver).copy(nullable = true)
                        ).defaultValue("null")
                            .build()

                        constructor.addParameter(parameterSpec)  // 添加参数到构造函数

                        // 构造一个与参数对应的属性并将其添加到类中
                        val propertySpec = PropertySpec.builder(parameterSpec.name, parameterSpec.type)
                            .initializer(parameterSpec.name)
                            .addModifiers(KModifier.PRIVATE)
                            .mutable(true)
                            .build()

                        addProperty(propertySpec)  // 添加属性到类
                    }
                }.build()
        )
        return this
    }

    private fun TypeSpec.Builder.buildFucImpl() {
        addFunction(
            FunSpec.builder("build")
                .addParameter(
                    ParameterSpec.builder(
                        "dsl", LambdaTypeName.get(
                            receiver = kotlinPoetResolver.fromClassname(classSimpleName + "Builder"),
                            parameters = emptyList(),
                            returnType = Unit::class.asTypeName()
                        )
                    ).build()
                )
                .addStatement("dsl()")
                .addCode(
                    CodeBlock.builder()
                        .add("return $classSimpleName(")
                        .also { code ->
                            parameters().forEach {
                                code.add("${it.name?.asString()!!} = this.${it.name?.asString()!!}!!,\n")
                            }
                        }
                        .add(")")
                        .build()
                )
                .returns(target.toClassName())
                .build()
        )
    }
}







