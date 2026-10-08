package space.seydlitz.dataslate.characters

import space.seydlitz.dataslate.api.ApiResult
import space.seydlitz.dataslate.api.CharacterWrite
import space.seydlitz.dataslate.api.StoredCharacter
import space.seydlitz.dataslate.auth.AuthRepository
import space.seydlitz.dataslate.sync.CharacterApi
import space.seydlitz.dataslate.sync.Changes
import space.seydlitz.dataslate.sync.RemoteCharacter
import space.seydlitz.dataslate.sync.SyncException
import space.seydlitz.dataslate.sync.WriteResult

// HTTP → результаты ядра; 401 разлогинивает в AuthRepository.
class ServerCharacterApi(private val auth: AuthRepository) : CharacterApi {
    override suspend fun changes(since: Long): Changes {
        val r = auth.characterChanges(since).orThrow()
        return Changes(r.cursor, r.changes.map { it.toRemote() })
    }

    override suspend fun put(id: String, json: String, base: Long) = write(auth.putCharacter(id, json, base))

    override suspend fun delete(id: String, base: Long) = write(auth.deleteCharacter(id, base))

    private fun write(result: ApiResult<CharacterWrite>): WriteResult {
        if (result is ApiResult.Failure) {
            when {
                result.status == 404 -> return WriteResult.NotFound
                result.code == "id_taken" -> return WriteResult.IdTaken
            }
        }
        return when (val w = result.orThrow()) {
            is CharacterWrite.Written -> WriteResult.Ok(w.stored.revision)
            is CharacterWrite.Conflict -> WriteResult.Conflict(w.server.toRemote())
        }
    }
}

private fun StoredCharacter.toRemote() = RemoteCharacter(id, revision, deleted, updatedAt, character?.toString())

private fun <T> ApiResult<T>.orThrow(): T = when (this) {
    is ApiResult.Ok -> value
    is ApiResult.Offline -> throw SyncException(true, cause.message ?: "offline")
    is ApiResult.Failure -> throw SyncException(false, "HTTP $status ${code.orEmpty()} ${requestId.orEmpty()}".trim())
}
