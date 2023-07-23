package dev.pausa.chemagno

data class Recipe(val id: String, val title: String, val notes: String? = null) {
    override fun equals(other: Any?) =
        when (other) {
            is Recipe -> id == other.id
            else -> false
        }

    override fun hashCode() = id.hashCode()
}