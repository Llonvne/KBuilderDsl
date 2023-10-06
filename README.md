# KBuilderDsl

#### A null-safe Kotlin builder pattern code compiler generator

### Introduction

Kotlin's data class is a handy tool, but when a data class has too many properties, we often encounter some difficulties during initialization.You might have seen the following code.
```kotlin
val newCall = IrConstructorCallImpl(
    0,
    0,
    type = implClassSymbol.defaultType,
    symbol = newConstructor,
    0,
    0,
    0,
    null
)
```
If your IDE does not provide variable name hints, it can be almost unreadable.Let's turn to a simpler example.
```kotlin
data class User(
    val username: String,
    val age: Int,
    val password: String = "123456",
    val nickname: String? = "llonvne",
    val name: String = username,
    val enable: Boolean = false
)
```
This is a typical user data class.But when you need to handle it, putting all the processing flow into the primary constructor could make it unbearably bloated, so we often write the following code.
```kotlin
val userId = 1
val username = Database.UserDb.byId(userId)?.username ?: "UNKNOWN"
/**
 * fetch some data...
 */
val user = User(username, password, nickname, name, false)
```
This results in many one-time variables being defined in the code, and they are used only once in the constructor. We hope to simplify this process.Here is a possible way to simplify it.

### Usage

```kotlin
val user = User.builder()
    .username { "123" }
    .age { 12 }
    .build {
        nickname { "HelloWorld" }
    }
```
Due to Kotlin's nullability requirements, we must enforce that users initialize all non-nullable properties that don't have default values at the beginning.In this example, both username and age are non-nullable and don't have default values, so they must be initialized in the order they are declared at the beginning.For other properties, you can build without initializing them, or you can initialize them in the same manner.
How do we implement this? We have generated the following two interfaces.
```kotlin
fun interface UserUsernameBuilder {
    fun username(builder: () -> String): UserAgeBuilder
}

fun interface UserAgeBuilder {
    fun age(builder: () -> Int): UserBuilder
}
```
These interfaces only provide functions to initialize the current property and then point to the next interface that needs to be initialized. Only when there are no non-nullable properties without default values will it point to the general Builder.With this approach, we can fully ensure that all non-nullable properties without initial values are correctly initialized.
The implementation is relatively straightforward. All interfaces are implemented in the general builder, and the preceding interfaces only point to a single object, just temporarily hiding some methods.That is to say, if you want to initialize in another way, or perform some actions that are not provided, you can also forcibly cast it to the general builder, but of course, this will lose the guarantee of null safety.
```kotlin
class UserBuilder(
    private var username: String? = null,
    private var age: Int? = null,
    private var password: String = "123456",
    private var nickname: String? = "123456",
    private var name: String? = null,
    private var enable: Boolean = false
) : UserUsernameBuilder, UserAgeBuilder {
    override fun username(builder: () -> String): UserBuilder {
        this.username = builder()
        return this
    }

    override fun age(builder: () -> Int): UserBuilder {
        this.age = builder()
        return this
    }

    fun password(builder: () -> String): UserBuilder {
        this.password = builder()
        return this
    }

    fun nickname(builder: () -> String?): UserBuilder {
        this.nickname = builder()
        return this
    }

    fun name(builder: () -> String): UserBuilder {
        this.name = builder()
        return this
    }

    fun enable(builder: () -> Boolean): UserBuilder {
        this.enable = builder()
        return this
    }

    fun build(dsl: UserBuilder.() -> Unit): User {
        return User(
            username = this.username!!,
            age = this.age!!,
            password = this.password,
            nickname = this.nickname,
            name = this.name ?: username!!,
            enable = this.enable
        )
    }
}
```
Use a simple annotation to enable it
```
annotation class BuilderDsl

@BuilderDsl
data class User(
    val username: String,
    val password: String = "123456",
    val nickname: String? = "llonvne",
    val name: String = username,
    val enable: Boolean = false
) 
```
Since Lombok uses the Builder annotation, and our Builder has more of a DSL style, I would like to name it BuilderDsl.
