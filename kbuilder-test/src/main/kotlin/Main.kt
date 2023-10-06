import cn.llonvne.userBuilder

@BuilderDsl
data class User(
    val username: String,
    val age: Int,
    val phone: String,
    val address: String?,
    val password: String = "123456",
    val nickname: String? = "llonvne",
    val name: String = username,
    val enable: Boolean = false
)

fun main() {
    val user = userBuilder()
        .username { "llonvne" }
        .age { 11 }
        .phone { "1508866666" }
        .address {  }
        .build { }
}