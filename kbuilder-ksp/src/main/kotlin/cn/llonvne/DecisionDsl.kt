package cn.llonvne

import kotlin.reflect.KClass

data class OnRejectDsl(var rejection: (() -> Unit)? = null) {
    fun onReject(block: () -> Unit) {
        rejection = block
    }
}

data class OnAcceptDsl(var acception: (() -> Unit)? = null) {
    fun onAccept(block: () -> Unit) {
        acception = block
    }
}

interface Decision {
    /**
     * 如果 [predicate] 为真作出 Accept 决策
     * 否则不做任何事
     */
    @DecisionDsl
    fun acceptIf(predicate: OnAcceptDsl.() -> Boolean) {
        val acceptDsl = OnAcceptDsl()
        if (predicate(acceptDsl)) {
            accept {
                acceptDsl.acception?.invoke()
            }
        } else { /* do Nothing*/
        }
    }

    /**
     * 如果 [predicate] 为真作出 Reject 决策
     * 否则不做任何事
     */
    @DecisionDsl
    fun rejectIf(predicate: OnRejectDsl.() -> Boolean) {
        val rejectDsl = OnRejectDsl()
        if (predicate(rejectDsl)) {
            reject {
                rejectDsl.rejection?.invoke()
            }
        } else { /* do Nothing*/
        }
    }

    /**
     * 如果 [predicate] 为真作出 Accept 决策
     * 否则作出 Reject 决策
     */
    @DecisionDsl
    fun decideOn(predicate: context(OnAcceptDsl, OnRejectDsl) () -> Boolean): Nothing {
        val onAc = OnAcceptDsl()
        val onRe = OnRejectDsl()
        if (predicate(onAc, onRe)) {
            accept { onAc.acception?.invoke() }
        } else {
            reject { onRe.rejection?.invoke() }
        }
    }

    /**
     * 该方法将立刻结束决策，并立刻作出 Accept
     */
    @DecisionDsl
    fun accept(next: () -> Unit = {}): Nothing

    /**
     * 该方法将立刻结束决策，并立刻作出 Reject
     */
    @DecisionDsl
    fun reject(next: () -> Unit = {}): Nothing

    /**
     * 该方法将待决策值设置为 Accept
     * 只有你在决策函数体中使用 decide 函数时，才会使用到待决策值
     */
    @DecisionDsl
    fun setAccept()

    /**
     * 该方法将待决策值设置为 Reject
     * 只有你在决策函数体中使用 decide 函数时，才会使用到待决策值
     */
    @DecisionDsl
    fun setReject()

    /**
     * 该函数将立刻以待决策值作出决策
     * 待决策值可以通过 [setAccept],[setAccept] 设置
     * @throws [DecisionUndefined] 错误，如果不存在一个有效的待决策值
     */
    @DecisionDsl
    fun decide(): Nothing

    @DecisionDsl
    fun <T : Exception> onErr(exceptionType: KClass<out Exception>, how: (T) -> Nothing)

    @DecisionDsl
    fun onDecisionUndefined(how: (DecisionUndefined) -> Nothing) = onErr<DecisionUndefined> { how(it) }
}

@DecisionDsl
inline fun <reified T : Exception> Decision.onErr(noinline how: (T) -> Nothing) = onErr(T::class, how)
class DecisionUndefined : Exception()
class DecisionMade : Exception()

class MaxResultInvoke : Exception()

@DslMarker
private annotation class DecisionDsl

private class DecisionDslImpl<R : Any>(
    private val onAccept: () -> R, private val onReject: () -> R, private val making: Decision.() -> Nothing
) : Decision {
    private enum class DecisionEnum {
        Accept, Reject, Undefined
    }

    private fun finish(): Nothing = throw DecisionMade()

    private var status: DecisionEnum = DecisionEnum.Undefined

    private val exceptionHandlers = mutableMapOf<KClass<out Exception>, (Exception) -> Nothing>()

    private val MAX_RESULT_INVOKE = 100
    private var cur_result_invoke = 0

    @Suppress("UNCHECKED_CAST")
    override fun <T : Exception> onErr(exceptionType: KClass<out Exception>, how: (T) -> Nothing) {
        exceptionHandlers[exceptionType] = how as (Exception) -> Nothing
    }

    @DecisionDsl
    override fun setAccept() {
        status = DecisionEnum.Accept
    }

    @DecisionDsl
    override fun setReject() {
        status = DecisionEnum.Reject
    }

    @DecisionDsl
    override fun accept(next: () -> Unit): Nothing {
        status = DecisionEnum.Accept
        next()
        finish()
    }

    @DecisionDsl
    override fun reject(next: () -> Unit): Nothing {
        status = DecisionEnum.Reject
        next()
        finish()
    }

    @DecisionDsl
    override fun decide(): Nothing = finish()

    fun result() = result(making)

    private fun result(making: Decision.() -> Nothing): R {
        if (cur_result_invoke < MAX_RESULT_INVOKE) {
            cur_result_invoke += 1
        } else {
            throw MaxResultInvoke()
        }
        return try {
            making()
        } catch (e: DecisionMade) {
            when (status) {
                DecisionEnum.Accept -> onAccept()
                DecisionEnum.Reject -> onReject()
                DecisionEnum.Undefined -> result { throw DecisionUndefined() }
            }
        } catch (e: Exception) {
            // 尝试寻找一个可以处理错误的处理函数，如果找不到，则抛出错误
            // 错误处理函数总是以 返回 Accept,Reject 或者抛出另外一个错误结束，递归的调用函数体
            val handler = exceptionHandlers[e::class]
            if (handler != null) {
                result {
                    handler.invoke(e)
                }
            } else {
                throw e
            }
        }
    }
}


private object AcceptTrue : () -> Boolean {
    override fun invoke(): Boolean {
        return true
    }
}

private object RejectFalse : () -> Boolean {
    override fun invoke(): Boolean {
        return false
    }
}

/**
 * makeDecision 允许你结构化，模块化处理复杂的逻辑判断功能
 * * 如果你在决策函数中没有使用 [DecisionDslImpl.decide] 函数，那么[makeDecision] 总能做出有效决定[DecisionEnum.Accept],[DecisionEnum.Reject]，不会抛出异常
 * * 如果你在决策函数中使用了[DecisionDslImpl.decide] 函数，那么如果之前没有执行任何的[DecisionDslImpl.setAccept],[DecisionDslImpl.setReject]更改待决策值，那么此时会抛出[DecisionUndefined]异常
 * * **请注意在 Decision DSL 中请勿直接捕获 Exception，由于Decision DSL实现中需要抛出 [DecisionMade] 错误来实现决策逻辑，如果确实需要捕获 Exception 请先捕获 [DecisionMade]
 * * 或者使用 onErr 函数来处理错误，该函数自动忽略 [DecisionMade] 错误
 * * 在任何一个异常处理函数中，你都可以重新作出一个决策，表示Accept,或者 Reject，你也可以抛出一个其他的错误等待被别的异常处理函数处理，或者向上传递错误，最多可以不超过254次异常处理调用，超过则会抛出[MaxResultInvoke]
 * * 如果你在决策函数中 accept{/*your code here throw exception*/} 那么本次决策将视为无效，此时将直接进入错误处理阶段
 * * 异常处理函数不受作用域的限制，一旦配置在整个[Decision]生命周期均有效，异常处理函数可以被覆盖，后运行的代码配置的相同的异常的处理函数可以覆盖前面配置的
 */
@Throws(DecisionUndefined::class, MaxResultInvoke::class)
fun <R : Any> makeDecision(onAccept: () -> R, onReject: () -> R, making: Decision.() -> Nothing) =
    DecisionDslImpl(onAccept, onReject, making).result()

/**
 * 这是一个 Boolean 类型的 makeDecision 的实现
 *  * 在 onAccept 返回时 true
 *  * 在 onReject 返回 false
 *  * **请注意在 Decision DSL 中请勿直接捕获 Exception，由于Decision DSL实现中需要抛出 [DecisionMade] 错误来实现决策逻辑，如果确实需要捕获 Exception 请先捕获 [DecisionMade]
 *  * 或者使用 onErr 函数来处理错误，该函数自动忽略 [DecisionMade] 错误
 *  * makeDecision 允许你结构化，模块化处理复杂的逻辑判断功能
 * * 如果你在决策函数中没有使用 [DecisionDslImpl.decide] 函数，那么[makeDecision] 总能做出有效决定[DecisionEnum.Accept],[DecisionEnum.Reject]，不会抛出异常
 * * 如果你在决策函数中使用了[DecisionDslImpl.decide] 函数，那么如果之前没有执行任何的[DecisionDslImpl.setAccept],[DecisionDslImpl.setReject]更改待决策值，那么此时会抛出[DecisionUndefined]异常
 * * 在任何一个异常处理函数中，你都可以重新作出一个决策，表示Accept,或者 Reject，你也可以抛出一个其他的错误等待被别的异常处理函数处理，或者向上传递错误，最多可以不超过254次异常处理调用，超过则会抛出[MaxResultInvoke]
 * * 如果你在决策函数中 accept{/*your code here throw exception*/} 那么本次决策将视为无效，此时将直接进入错误处理阶段,由于 accept 与 reject 本身也依赖错误处理机制，所以如果在 accept 与 reject 调用 函数也会被覆盖
 * * 异常处理函数不受作用域的限制，一旦配置在整个[Decision]生命周期均有效，异常处理函数可以被覆盖，后运行的代码配置的相同的异常的处理函数可以覆盖前面配置的
 * */
@Throws(DecisionUndefined::class, MaxResultInvoke::class)
fun makeDecision(making: Decision.() -> Nothing): Boolean = DecisionDslImpl(AcceptTrue, RejectFalse, making).result()

fun <T> List<T>.filterDecision(making: Decision.(T) -> Nothing): List<T> = filter { makeDecision { making(it) } }

fun main() {
    println(makeDecision {
        onErr<DecisionMade> {
            println("!!$it")
            accept()
        }

        try {
            accept()
        } catch (e: DecisionMade) {
            println(e)
        }

        accept()
    })
}