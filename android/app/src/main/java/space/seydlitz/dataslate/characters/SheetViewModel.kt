package space.seydlitz.dataslate.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import space.seydlitz.dataslate.content.CharacterFile

sealed interface SheetState {
    data object Loading : SheetState
    data object Missing : SheetState
    data class Ready(val file: CharacterFile) : SheetState
}

// Открытая анкета: правки — сразу в состояние и в автосохранение репозитория.
class SheetViewModel(private val repo: CharacterRepository, val id: String) : ViewModel() {
    private val _state = MutableStateFlow<SheetState>(SheetState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        // Обновлена с сервера или после выбора в конфликте.
        viewModelScope.launch { repo.reloads.filter { it == id }.collect { load() } }
    }

    private suspend fun load() {
        _state.value = repo.get(id)?.let { SheetState.Ready(it) } ?: SheetState.Missing
    }

    fun edit(change: (CharacterFile) -> CharacterFile) {
        val current = (_state.value as? SheetState.Ready)?.file ?: return
        val next = change(current)
        if (next == current) return
        _state.value = SheetState.Ready(next)
        repo.scheduleSave(next)
    }

    suspend fun delete() = repo.remove(id)
}
