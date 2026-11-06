package cz.mares.bombman

import kotlin.random.Random

enum class SoundEffect {
    PLACE_BOMB,
    EXPLOSION,
    POWERUP,
    ENEMY_KILL,
    PLAYER_DIE,
    LEVEL_CLEAR,
    KICK_BOMB
}

data class ScoreEntry(
    val nick: String,
    val score: Int,
    val deviceId: String = ""
)

expect object PlatformService {
    fun loadString(key: String, default: String = ""): String
    fun saveString(key: String, value: String)
    fun loadInt(key: String, default: Int = 0): Int
    fun saveInt(key: String, value: Int)
    fun playSound(effect: SoundEffect)
    fun httpGet(url: String, callback: (Boolean, String) -> Unit)
    fun httpPut(url: String, jsonBody: String, callback: (Int, String) -> Unit)
}

object LeaderboardRepository {
    private const val BASE_URL = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"
    private const val KEY_DEVICE_ID = "bombman_device_id"
    private const val KEY_NICK = "bombman_player_nick"
    private const val KEY_SOUND = "bombman_sound_enabled"

    var soundEnabled: Boolean
        get() = PlatformService.loadInt(KEY_SOUND, 1) == 1
        set(value) = PlatformService.saveInt(KEY_SOUND, if (value) 1 else 0)

    fun play(effect: SoundEffect) {
        if (soundEnabled) {
            PlatformService.playSound(effect)
        }
    }

    fun getOrCreateDeviceId(): String {
        val existing = PlatformService.loadString(KEY_DEVICE_ID, "")
        if (existing.isNotBlank()) return existing
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder("dev_")
        for (i in 0 until 20) {
            sb.append(chars[Random.nextInt(chars.length)])
        }
        val created = sb.toString()
        PlatformService.saveString(KEY_DEVICE_ID, created)
        return created
    }

    fun getSavedNick(): String {
        return PlatformService.loadString(KEY_NICK, "").trim()
    }

    fun saveNickLocal(nick: String) {
        PlatformService.saveString(KEY_NICK, nick.trim())
    }

    fun sanitizeNick(raw: String): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-"
        val filtered = buildString {
            for (c in raw.trim()) {
                if (c in allowed) append(c)
                else if (c == ' ') append('_')
            }
        }
        return if (filtered.length > 14) filtered.substring(0, 14) else filtered
    }

    fun getLocalBestScore(gameId: String): Int {
        return PlatformService.loadInt("best_$gameId", 0)
    }

    fun setLocalBestScore(gameId: String, score: Int) {
        val current = getLocalBestScore(gameId)
        if (score > current) {
            PlatformService.saveInt("best_$gameId", score)
        }
    }

    fun fetchTopScores(
        gameId: String,
        includeZeroScores: Boolean = false,
        onResult: (Boolean, List<ScoreEntry>, String?) -> Unit
    ) {
        val url = "$BASE_URL/high_scores/$gameId.json"
        PlatformService.httpGet(url) { ok, body ->
            if (!ok) {
                onResult(false, emptyList(), "Chyba připojení k serveru.")
                return@httpGet
            }
            val trimmed = body.trim()
            if (trimmed.isEmpty() || trimmed == "null" || trimmed == "{}") {
                onResult(true, emptyList(), null)
                return@httpGet
            }
            val parsed = parseScoresJson(trimmed)
                .filter { includeZeroScores || it.score > 0 }
                .sortedByDescending { it.score }
                .take(50)
            onResult(true, parsed, null)
        }
    }

    fun submitScore(
        gameId: String,
        rawNick: String,
        score: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        val nick = sanitizeNick(rawNick)
        if (nick.length < 2) {
            onResult(false, "Přezdívka musí mít alespoň 2 znaky (A-Z, 0-9).")
            return
        }
        val myDeviceId = getOrCreateDeviceId()
        val entryUrl = "$BASE_URL/high_scores/$gameId/$nick.json"

        PlatformService.httpGet(entryUrl) { getOk, getBody ->
            if (!getOk) {
                onResult(false, "Nelze ověřit přezdívku. Zkontroluj internet.")
                return@httpGet
            }
            val clean = getBody.trim()
            var existingScore = -1
            var existingDev = ""

            if (clean.isNotEmpty() && clean != "null") {
                existingScore = extractIntField(clean, "score") ?: clean.toIntOrNull() ?: -1
                existingDev = extractStringField(clean, "deviceId") ?: ""
            }

            if (existingDev.isNotEmpty() && existingDev != myDeviceId) {
                onResult(false, "Přezdívka '$nick' je již obsazena jiným hráčem!")
                return@httpGet
            }

            val bestToSave = if (score > existingScore) score else existingScore
            if (bestToSave <= existingScore && existingScore >= 0) {
                saveNickLocal(nick)
                setLocalBestScore(gameId, bestToSave)
                onResult(true, "Přezdívka uložena.")
                return@httpGet
            }

            val finalScore = if (bestToSave < 0) 0 else bestToSave
            val jsonPayload = """{"score":$finalScore,"deviceId":"$myDeviceId"}"""

            PlatformService.httpPut(entryUrl, jsonPayload) { code, _ ->
                if (code in 200..299) {
                    saveNickLocal(nick)
                    setLocalBestScore(gameId, finalScore)
                    onResult(true, "Rekord úspěšně uložen!")
                } else if (code == 401 || code == 403) {
                    onResult(false, "Přezdívka '$nick' je chráněna nebo obsazena.")
                } else {
                    onResult(false, "Chyba při zápisu (HTTP $code).")
                }
            }
        }
    }

    private fun parseScoresJson(json: String): List<ScoreEntry> {
        val result = mutableListOf<ScoreEntry>()
        val content = json.trim().removeSurrounding("{", "}")
        if (content.isBlank()) return result

        var i = 0
        val n = content.length
        while (i < n) {
            while (i < n && (content[i].isWhitespace() || content[i] == ',')) i++
            if (i >= n || content[i] != '"') break
            val keyStart = i + 1
            val keyEnd = content.indexOf('"', keyStart)
            if (keyEnd < 0) break
            val nick = content.substring(keyStart, keyEnd)
            i = keyEnd + 1

            while (i < n && (content[i].isWhitespace() || content[i] == ':')) i++
            if (i >= n) break

            if (content[i] == '{') {
                var depth = 0
                val objStart = i
                while (i < n) {
                    if (content[i] == '{') depth++
                    else if (content[i] == '}') {
                        depth--
                        if (depth == 0) {
                            i++
                            break
                        }
                    }
                    i++
                }
                val objStr = content.substring(objStart, i)
                val sc = extractIntField(objStr, "score") ?: 0
                val dev = extractStringField(objStr, "deviceId") ?: ""
                result.add(ScoreEntry(nick = nick, score = sc, deviceId = dev))
            } else {
                val valStart = i
                while (i < n && content[i] != ',' && content[i] != '}') i++
                val rawVal = content.substring(valStart, i).trim()
                val sc = rawVal.toIntOrNull() ?: 0
                result.add(ScoreEntry(nick = nick, score = sc))
            }
        }
        return result
    }

    private fun extractIntField(jsonObj: String, field: String): Int? {
        val token = "\"$field\""
        val idx = jsonObj.indexOf(token)
        if (idx < 0) return null
        val colon = jsonObj.indexOf(':', idx + token.length)
        if (colon < 0) return null
        var p = colon + 1
        while (p < jsonObj.length && jsonObj[p].isWhitespace()) p++
        val start = p
        while (p < jsonObj.length && (jsonObj[p].isDigit() || jsonObj[p] == '-')) p++
        return jsonObj.substring(start, p).toIntOrNull()
    }

    private fun extractStringField(jsonObj: String, field: String): String? {
        val token = "\"$field\""
        val idx = jsonObj.indexOf(token)
        if (idx < 0) return null
        val colon = jsonObj.indexOf(':', idx + token.length)
        if (colon < 0) return null
        val q1 = jsonObj.indexOf('"', colon + 1)
        if (q1 < 0) return null
        val q2 = jsonObj.indexOf('"', q1 + 1)
        if (q2 < 0) return null
        return jsonObj.substring(q1 + 1, q2)
    }
}
