package cz.mares.bombman

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

enum class GameEdition(
    val id: String,
    val title: String,
    val shortTab: String,
    val year: String,
    val description: String,
    val firebaseKey: String
) {
    DYNA(
        id = "dyna",
        title = "DYNA BLASTER",
        shortTab = "DYNA",
        year = "1992",
        description = "Klasická DOS kampaň: bílý panáček, cihlové zdi, balónkové příšerky a hledání průchodu do dalšího kola.",
        firebaseKey = "bombman_dyna"
    ),
    BOMBIC(
        id = "bombic",
        title = "BOMBÍČ",
        shortTab = "BOMBÍČ",
        year = "2006",
        description = "Česká červíková legenda: zahradní bludiště, keře, dřevěné bedny, brouci a podzemní nora.",
        firebaseKey = "bombman_bombic"
    ),
    ATOMIC(
        id = "atomic",
        title = "ATOMIC BOMBERMAN",
        shortTab = "ATOMIC",
        year = "1997",
        description = "Arénová bitva proti 3 umělým Bombermanům, kteří pokládají bomby. Obsahuje kopání bomb!",
        firebaseKey = "bombman_atomic"
    ),
    MRBOOM(
        id = "mrboom",
        title = "MR. BOOM",
        shortTab = "MR. BOOM",
        year = "1999",
        description = "Rychlá retro aréna: kombinace AI soupeřů a příšerek, spousta bonusů a padající strop před koncem času.",
        firebaseKey = "bombman_mrboom"
    )
}

enum class Difficulty(val label: String, val scoreMult: Int, val enemyMoveInterval: Int) {
    EASY("LEHKÁ", 1, 11),
    NORMAL("STŘEDNÍ", 2, 8),
    HARD("TĚŽKÁ", 3, 6)
}

enum class TileType {
    EMPTY,
    HARD_WALL,
    SOFT_WALL
}

enum class PowerUpType {
    NONE,
    EXTRA_BOMB,
    FIRE_UP,
    SPEED_UP,
    KICK_BOMB,
    EXTRA_LIFE
}

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    NONE(0, 0)
}

enum class EnemyKind {
    BALLOON,
    GHOST,
    BEETLE,
    RIVAL_RED,
    RIVAL_BLUE,
    RIVAL_GOLD
}

data class BombItem(
    val x: Int,
    val y: Int,
    var ticksLeft: Int,
    val range: Int,
    val ownerIsPlayer: Boolean,
    var slideDir: Direction = Direction.NONE
)

data class FlameCell(
    val x: Int,
    val y: Int,
    var ticksLeft: Int
)

data class EnemyActor(
    val id: Int,
    var x: Int,
    var y: Int,
    val kind: EnemyKind,
    var dir: Direction,
    var moveCooldown: Int,
    var bombCooldown: Int = 0,
    var alive: Boolean = true
)

class GameEngine {
    val gridWidth = 13
    val gridHeight = 11

    var edition by mutableStateOf(GameEdition.DYNA)
    var difficulty by mutableStateOf(Difficulty.NORMAL)

    var level by mutableStateOf(1)
    var score by mutableStateOf(0)
    var lives by mutableStateOf(3)
    var timeLeftSeconds by mutableStateOf(150)

    var playerX by mutableStateOf(1)
    var playerY by mutableStateOf(1)
    var playerFacing by mutableStateOf(Direction.DOWN)
    var playerInvulnerableTicks by mutableStateOf(0)

    var maxBombs by mutableStateOf(1)
    var fireRange by mutableStateOf(2)
    var hasKick by mutableStateOf(false)
    var speedLevel by mutableStateOf(1)

    var isGameOver by mutableStateOf(false)
    var isVictoryBanner by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var statusMessage by mutableStateOf("Znič nepřátele a najdi východ!")

    var exitX by mutableStateOf(-1)
    var exitY by mutableStateOf(-1)
    var exitRevealed by mutableStateOf(false)

    var renderTick by mutableStateOf(0)

    private val tiles = Array(gridWidth) { Array(gridHeight) { TileType.EMPTY } }
    private val hiddenPowerUps = Array(gridWidth) { Array(gridHeight) { PowerUpType.NONE } }
    private val visiblePowerUps = Array(gridWidth) { Array(gridHeight) { PowerUpType.NONE } }

    val bombs = mutableListOf<BombItem>()
    val flames = mutableListOf<FlameCell>()
    val enemies = mutableListOf<EnemyActor>()

    private var subTickCounter = 0
    private var playerMoveCooldown = 0
    private var nextEnemyId = 1
    private var suddenDeathIndex = 0

    fun getTile(x: Int, y: Int): TileType {
        if (x !in 0 until gridWidth || y !in 0 until gridHeight) return TileType.HARD_WALL
        return tiles[x][y]
    }

    fun getVisiblePowerUp(x: Int, y: Int): PowerUpType {
        if (x !in 0 until gridWidth || y !in 0 until gridHeight) return PowerUpType.NONE
        return visiblePowerUps[x][y]
    }

    fun startNewGame(selectedEdition: GameEdition = edition, selectedDiff: Difficulty = difficulty) {
        edition = selectedEdition
        difficulty = selectedDiff
        level = 1
        score = 0
        lives = 3
        maxBombs = if (edition == GameEdition.MRBOOM) 2 else 1
        fireRange = if (edition == GameEdition.MRBOOM) 2 else 1
        hasKick = (edition == GameEdition.ATOMIC)
        speedLevel = 1
        isGameOver = false
        isVictoryBanner = false
        isPaused = false
        setupLevel()
    }

    fun setupLevel() {
        bombs.clear()
        flames.clear()
        enemies.clear()
        playerX = 1
        playerY = 1
        playerFacing = Direction.DOWN
        playerInvulnerableTicks = 35
        playerMoveCooldown = 0
        subTickCounter = 0
        suddenDeathIndex = 0
        exitRevealed = false
        exitX = -1
        exitY = -1
        timeLeftSeconds = if (edition == GameEdition.MRBOOM) 95 else 150

        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                hiddenPowerUps[x][y] = PowerUpType.NONE
                visiblePowerUps[x][y] = PowerUpType.NONE
                val isBorder = (x == 0 || y == 0 || x == gridWidth - 1 || y == gridHeight - 1)
                val isPillar = (x % 2 == 0 && y % 2 == 0)
                tiles[x][y] = if (isBorder || isPillar) TileType.HARD_WALL else TileType.EMPTY
            }
        }

        val safeZones = setOf(
            Pair(1, 1), Pair(2, 1), Pair(1, 2),
            Pair(gridWidth - 2, 1), Pair(gridWidth - 3, 1), Pair(gridWidth - 2, 2),
            Pair(1, gridHeight - 2), Pair(2, gridHeight - 2), Pair(1, gridHeight - 3),
            Pair(gridWidth - 2, gridHeight - 2), Pair(gridWidth - 3, gridHeight - 2), Pair(gridWidth - 2, gridHeight - 3)
        )

        val candidateSoftWalls = mutableListOf<Pair<Int, Int>>()
        val softDensity = when (edition) {
            GameEdition.DYNA -> 0.56f
            GameEdition.BOMBIC -> 0.60f
            GameEdition.ATOMIC -> 0.50f
            GameEdition.MRBOOM -> 0.48f
        }

        for (x in 1 until gridWidth - 1) {
            for (y in 1 until gridHeight - 1) {
                if (tiles[x][y] == TileType.EMPTY && Pair(x, y) !in safeZones) {
                    if (Random.nextFloat() < softDensity) {
                        tiles[x][y] = TileType.SOFT_WALL
                        candidateSoftWalls.add(Pair(x, y))
                    }
                }
            }
        }

        candidateSoftWalls.shuffle()

        if (edition == GameEdition.DYNA || edition == GameEdition.BOMBIC) {
            if (candidateSoftWalls.isNotEmpty()) {
                val exitPos = candidateSoftWalls.removeAt(0)
                exitX = exitPos.first
                exitY = exitPos.second
            } else {
                exitX = gridWidth - 2
                exitY = gridHeight - 2
                exitRevealed = true
            }
        }

        val powerUpList = mutableListOf(
            PowerUpType.EXTRA_BOMB,
            PowerUpType.FIRE_UP,
            PowerUpType.EXTRA_BOMB,
            PowerUpType.FIRE_UP,
            PowerUpType.SPEED_UP,
            PowerUpType.KICK_BOMB
        )
        if (edition == GameEdition.MRBOOM || edition == GameEdition.ATOMIC) {
            powerUpList.add(PowerUpType.EXTRA_BOMB)
            powerUpList.add(PowerUpType.FIRE_UP)
            powerUpList.add(PowerUpType.KICK_BOMB)
        }
        if (level % 2 == 0) {
            powerUpList.add(PowerUpType.EXTRA_LIFE)
        }

        for (pu in powerUpList) {
            if (candidateSoftWalls.isEmpty()) break
            val pos = candidateSoftWalls.removeAt(0)
            hiddenPowerUps[pos.first][pos.second] = pu
        }

        spawnEnemiesForEdition()

        statusMessage = when (edition) {
            GameEdition.DYNA -> "Kolo $level: Znič příšerky a najdi tajné dveře!"
            GameEdition.BOMBIC -> "Kolo $level: Vyčisti zahradu od brouků a vlez do nory!"
            GameEdition.ATOMIC -> "Aréna $level: Poraz všechny 3 soupeře Bombermanů!"
            GameEdition.MRBOOM -> "Aréna $level: Rychlá řežba! Pozor na padající strop!"
        }
        renderTick++
    }

    private fun spawnEnemiesForEdition() {
        val corners = listOf(
            Pair(gridWidth - 2, 1),
            Pair(1, gridHeight - 2),
            Pair(gridWidth - 2, gridHeight - 2)
        )

        when (edition) {
            GameEdition.ATOMIC -> {
                val rivalKinds = listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE, EnemyKind.RIVAL_GOLD)
                for (i in corners.indices) {
                    val c = corners[i]
                    enemies.add(
                        EnemyActor(
                            id = nextEnemyId++,
                            x = c.first,
                            y = c.second,
                            kind = rivalKinds[i],
                            dir = Direction.LEFT,
                            moveCooldown = difficulty.enemyMoveInterval
                        )
                    )
                }
            }
            GameEdition.MRBOOM -> {
                val rivalKinds = listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE)
                for (i in rivalKinds.indices) {
                    val c = corners[i]
                    enemies.add(
                        EnemyActor(
                            id = nextEnemyId++,
                            x = c.first,
                            y = c.second,
                            kind = rivalKinds[i],
                            dir = Direction.LEFT,
                            moveCooldown = (difficulty.enemyMoveInterval - 1).coerceAtLeast(4)
                        )
                    )
                }
                spawnMonsters(2 + level.coerceAtMost(4), EnemyKind.BALLOON, EnemyKind.GHOST)
            }
            GameEdition.BOMBIC -> {
                val count = 3 + level + difficulty.ordinal
                spawnMonsters(count, EnemyKind.BEETLE, EnemyKind.GHOST)
            }
            GameEdition.DYNA -> {
                val count = 3 + level + difficulty.ordinal
                spawnMonsters(count, EnemyKind.BALLOON, EnemyKind.GHOST)
            }
        }
    }

    private fun spawnMonsters(count: Int, primaryKind: EnemyKind, secondaryKind: EnemyKind) {
        val freeCells = mutableListOf<Pair<Int, Int>>()
        for (x in 1 until gridWidth - 1) {
            for (y in 1 until gridHeight - 1) {
                if (tiles[x][y] == TileType.EMPTY && (x > 3 || y > 3)) {
                    freeCells.add(Pair(x, y))
                }
            }
        }
        freeCells.shuffle()
        val actualCount = count.coerceAtMost(freeCells.size)
        for (i in 0 until actualCount) {
            val pos = freeCells[i]
            val kind = if (i % 3 == 2 && level >= 2) secondaryKind else primaryKind
            enemies.add(
                EnemyActor(
                    id = nextEnemyId++,
                    x = pos.first,
                    y = pos.second,
                    kind = kind,
                    dir = Direction.entries[Random.nextInt(4)],
                    moveCooldown = difficulty.enemyMoveInterval
                )
            )
        }
    }

    fun movePlayer(dir: Direction) {
        if (isGameOver || isPaused || isVictoryBanner) return
        if (playerMoveCooldown > 0) return

        playerFacing = dir
        val nx = playerX + dir.dx
        val ny = playerY + dir.dy
        if (nx !in 1 until gridWidth - 1 || ny !in 1 until gridHeight - 1) return

        val bombAtTarget = bombs.firstOrNull { it.x == nx && it.y == ny }
        if (bombAtTarget != null) {
            if (hasKick) {
                val bx = bombAtTarget.x + dir.dx
                val by = bombAtTarget.y + dir.dy
                if (canBombSlideTo(bx, by)) {
                    bombAtTarget.slideDir = dir
                    LeaderboardRepository.play(SoundEffect.KICK_BOMB)
                }
            }
            return
        }

        if (tiles[nx][ny] != TileType.EMPTY) return

        playerX = nx
        playerY = ny
        playerMoveCooldown = (3 - (speedLevel - 1)).coerceAtLeast(1)

        val pu = visiblePowerUps[nx][ny]
        if (pu != PowerUpType.NONE) {
            visiblePowerUps[nx][ny] = PowerUpType.NONE
            applyPowerUp(pu)
        }

        if (exitRevealed && nx == exitX && ny == exitY && enemies.none { it.alive }) {
            advanceToNextLevel()
            return
        }

        checkPlayerHitByEnemyOrFlame()
        renderTick++
    }

    private fun applyPowerUp(pu: PowerUpType) {
        LeaderboardRepository.play(SoundEffect.POWERUP)
        addScore(50 * difficulty.scoreMult)
        when (pu) {
            PowerUpType.EXTRA_BOMB -> {
                maxBombs = (maxBombs + 1).coerceAtMost(6)
                statusMessage = "Bonus: +1 Bomba (celkem $maxBombs)!"
            }
            PowerUpType.FIRE_UP -> {
                fireRange = (fireRange + 1).coerceAtMost(6)
                statusMessage = "Bonus: Delší plamen (dosah $fireRange)!"
            }
            PowerUpType.SPEED_UP -> {
                speedLevel = (speedLevel + 1).coerceAtMost(3)
                statusMessage = "Bonus: Rychlejší boty!"
            }
            PowerUpType.KICK_BOMB -> {
                hasKick = true
                statusMessage = "Bonus: Kopání bomb aktivováno!"
            }
            PowerUpType.EXTRA_LIFE -> {
                lives = (lives + 1).coerceAtMost(9)
                statusMessage = "Bonus: Extra život ($lives)!"
            }
            PowerUpType.NONE -> {}
        }
    }

    fun placeBomb() {
        if (isGameOver || isPaused || isVictoryBanner) return
        val activePlayerBombs = bombs.count { it.ownerIsPlayer }
        if (activePlayerBombs >= maxBombs) return
        if (bombs.any { it.x == playerX && it.y == playerY }) return

        bombs.add(
            BombItem(
                x = playerX,
                y = playerY,
                ticksLeft = 45,
                range = fireRange,
                ownerIsPlayer = true
            )
        )
        LeaderboardRepository.play(SoundEffect.PLACE_BOMB)
        renderTick++
    }

    fun tick() {
        if (isGameOver || isPaused) return

        if (isVictoryBanner) {
            subTickCounter++
            if (subTickCounter >= 35) {
                isVictoryBanner = false
                level++
                setupLevel()
            }
            renderTick++
            return
        }

        if (playerMoveCooldown > 0) playerMoveCooldown--
        if (playerInvulnerableTicks > 0) playerInvulnerableTicks--

        subTickCounter++
        if (subTickCounter % 20 == 0) {
            if (timeLeftSeconds > 0) {
                timeLeftSeconds--
                if (edition == GameEdition.MRBOOM && timeLeftSeconds <= 30 && timeLeftSeconds % 2 == 0) {
                    triggerSuddenDeathBlock()
                }
            } else {
                damagePlayer("Vypršel čas!")
            }
        }

        updateSlidingBombs()
        updateBombsAndFlames()
        updateEnemies()
        checkPlayerHitByEnemyOrFlame()

        if ((edition == GameEdition.ATOMIC || edition == GameEdition.MRBOOM) && enemies.none { it.alive }) {
            advanceToNextLevel()
        }

        renderTick++
    }

    private fun triggerSuddenDeathBlock() {
        val ringCoords = mutableListOf<Pair<Int, Int>>()
        for (x in 1 until gridWidth - 1) {
            ringCoords.add(Pair(x, 1))
            ringCoords.add(Pair(x, gridHeight - 2))
        }
        for (y in 2 until gridHeight - 2) {
            ringCoords.add(Pair(1, y))
            ringCoords.add(Pair(gridWidth - 2, y))
        }
        if (suddenDeathIndex < ringCoords.size) {
            val target = ringCoords[suddenDeathIndex++]
            if (target.first != playerX || target.second != playerY) {
                tiles[target.first][target.second] = TileType.HARD_WALL
                visiblePowerUps[target.first][target.second] = PowerUpType.NONE
            }
        }
    }

    private fun canBombSlideTo(x: Int, y: Int): Boolean {
        if (x !in 1 until gridWidth - 1 || y !in 1 until gridHeight - 1) return false
        if (tiles[x][y] != TileType.EMPTY) return false
        if (bombs.any { it.x == x && it.y == y }) return false
        if (enemies.any { it.alive && it.x == x && it.y == y }) return false
        return true
    }

    private fun updateSlidingBombs() {
        for (b in bombs) {
            if (b.slideDir != Direction.NONE) {
                val nx = b.x + b.slideDir.dx
                val ny = b.y + b.slideDir.dy
                if (canBombSlideTo(nx, ny)) {
                    val idx = bombs.indexOf(b)
                    if (idx >= 0) {
                        bombs[idx] = b.copy(x = nx, y = ny)
                    }
                } else {
                    b.slideDir = Direction.NONE
                }
            }
        }
    }

    private fun updateBombsAndFlames() {
        val iterator = flames.iterator()
        while (iterator.hasNext()) {
            val f = iterator.next()
            f.ticksLeft--
            if (f.ticksLeft <= 0) iterator.remove()
        }

        val toExplode = mutableListOf<BombItem>()
        for (b in bombs) {
            b.ticksLeft--
            if (b.ticksLeft <= 0) {
                toExplode.add(b)
            }
        }

        val processed = mutableSetOf<BombItem>()
        while (toExplode.isNotEmpty()) {
            val b = toExplode.removeAt(0)
            if (!processed.add(b)) continue
            bombs.remove(b)
            explodeBomb(b, toExplode)
        }
    }

    private fun explodeBomb(bomb: BombItem, chainQueue: MutableList<BombItem>) {
        LeaderboardRepository.play(SoundEffect.EXPLOSION)
        addFlame(bomb.x, bomb.y)

        val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)
        for (d in dirs) {
            for (step in 1..bomb.range) {
                val tx = bomb.x + d.dx * step
                val ty = bomb.y + d.dy * step
                if (tx !in 1 until gridWidth - 1 || ty !in 1 until gridHeight - 1) break

                val tile = tiles[tx][ty]
                if (tile == TileType.HARD_WALL) break

                val otherBomb = bombs.firstOrNull { it.x == tx && it.y == ty }
                if (otherBomb != null) {
                    chainQueue.add(otherBomb)
                }

                if (tile == TileType.SOFT_WALL) {
                    tiles[tx][ty] = TileType.EMPTY
                    addFlame(tx, ty)
                    addScore(10 * difficulty.scoreMult)
                    if (tx == exitX && ty == exitY) {
                        exitRevealed = true
                        statusMessage = "Našel jsi východ! Znič zbylé nepřátele!"
                    } else if (hiddenPowerUps[tx][ty] != PowerUpType.NONE) {
                        visiblePowerUps[tx][ty] = hiddenPowerUps[tx][ty]
                        hiddenPowerUps[tx][ty] = PowerUpType.NONE
                    }
                    break
                } else {
                    visiblePowerUps[tx][ty] = PowerUpType.NONE
                    addFlame(tx, ty)
                }
            }
        }
    }

    private fun addFlame(x: Int, y: Int) {
        val existing = flames.firstOrNull { it.x == x && it.y == y }
        if (existing != null) {
            existing.ticksLeft = 14
        } else {
            flames.add(FlameCell(x = x, y = y, ticksLeft = 14))
        }
    }

    private fun updateEnemies() {
        for (e in enemies) {
            if (!e.alive) continue

            if (flames.any { it.x == e.x && it.y == e.y }) {
                e.alive = false
                val pts = if (e.kind in listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE, EnemyKind.RIVAL_GOLD)) 300 else 100
                addScore(pts * difficulty.scoreMult)
                LeaderboardRepository.play(SoundEffect.ENEMY_KILL)
                val remaining = enemies.count { it.alive }
                statusMessage = if (remaining == 0) {
                    if (edition == GameEdition.DYNA || edition == GameEdition.BOMBIC) {
                        "Všichni nepřátelé zničeni! Běž do východu!"
                    } else {
                        "Aréna vyčištěna!"
                    }
                } else {
                    "Zásah! Zbývá nepřátel: $remaining"
                }
                continue
            }

            if (e.bombCooldown > 0) e.bombCooldown--
            if (e.moveCooldown > 0) {
                e.moveCooldown--
                continue
            }

            val isRival = e.kind in listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE, EnemyKind.RIVAL_GOLD)
            val canPassSoftWalls = (e.kind == EnemyKind.GHOST)

            val possibleDirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT).filter { d ->
                val nx = e.x + d.dx
                val ny = e.y + d.dy
                val inBounds = nx in 1 until gridWidth - 1 && ny in 1 until gridHeight - 1
                val tileOk = if (canPassSoftWalls) {
                    tiles[nx][ny] != TileType.HARD_WALL
                } else {
                    tiles[nx][ny] == TileType.EMPTY
                }
                val noBomb = bombs.none { it.x == nx && it.y == ny }
                val noFlame = flames.none { it.x == nx && it.y == ny }
                inBounds && tileOk && noBomb && noFlame
            }

            if (possibleDirs.isNotEmpty()) {
                val chosenDir = if (isRival) {
                    val safeDirs = possibleDirs.filter { d -> !isTileInDanger(e.x + d.dx, e.y + d.dy) }
                    val pool = if (safeDirs.isNotEmpty()) safeDirs else possibleDirs
                    pool.minByOrNull { d ->
                        abs((e.x + d.dx) - playerX) + abs((e.y + d.dy) - playerY) + Random.nextInt(3)
                    } ?: pool.first()
                } else {
                    if (e.dir in possibleDirs && Random.nextFloat() < 0.65f) {
                        e.dir
                    } else {
                        possibleDirs[Random.nextInt(possibleDirs.size)]
                    }
                }

                e.dir = chosenDir
                e.x += chosenDir.dx
                e.y += chosenDir.dy

                if (isRival) {
                    val pu = visiblePowerUps[e.x][e.y]
                    if (pu != PowerUpType.NONE) {
                        visiblePowerUps[e.x][e.y] = PowerUpType.NONE
                    }
                    val nearTarget = (abs(e.x - playerX) + abs(e.y - playerY) <= 2) ||
                        listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT).any { d ->
                            getTile(e.x + d.dx, e.y + d.dy) == TileType.SOFT_WALL
                        }
                    if (nearTarget && e.bombCooldown == 0 && bombs.none { it.x == e.x && it.y == e.y }) {
                        if (Random.nextFloat() < 0.35f) {
                            bombs.add(
                                BombItem(
                                    x = e.x,
                                    y = e.y,
                                    ticksLeft = 48,
                                    range = 2,
                                    ownerIsPlayer = false
                                )
                            )
                            e.bombCooldown = 65
                        }
                    }
                }
            }

            e.moveCooldown = difficulty.enemyMoveInterval
        }
    }

    private fun isTileInDanger(tx: Int, ty: Int): Boolean {
        for (b in bombs) {
            if (b.x == tx && abs(b.y - ty) <= b.range) return true
            if (b.y == ty && abs(b.x - tx) <= b.range) return true
        }
        return false
    }

    private fun checkPlayerHitByEnemyOrFlame() {
        if (playerInvulnerableTicks > 0 || isGameOver || isVictoryBanner) return

        val hitFlame = flames.any { it.x == playerX && it.y == playerY }
        val hitEnemy = enemies.any { it.alive && it.x == playerX && it.y == playerY && it.kind !in listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE, EnemyKind.RIVAL_GOLD) }

        if (hitFlame || hitEnemy) {
            damagePlayer(if (hitFlame) "Zasáhl tě plamen bomby!" else "Chytila tě příšerka!")
        }
    }

    private fun damagePlayer(reason: String) {
        LeaderboardRepository.play(SoundEffect.PLAYER_DIE)
        lives--
        if (lives <= 0) {
            lives = 0
            isGameOver = true
            statusMessage = "$reason Konec hry!"
            saveHighScoreIfNeeded()
        } else {
            playerX = 1
            playerY = 1
            playerInvulnerableTicks = 45
            statusMessage = "$reason Zbývají životy: $lives"
        }
    }

    private fun advanceToNextLevel() {
        LeaderboardRepository.play(SoundEffect.LEVEL_CLEAR)
        val timeBonus = (timeLeftSeconds * 5 * difficulty.scoreMult).coerceAtLeast(100)
        addScore(500 * difficulty.scoreMult + timeBonus)
        isVictoryBanner = true
        subTickCounter = 0
        statusMessage = "Kolo $level dokončeno! Bonus +$timeBonus bodů!"
        saveHighScoreIfNeeded()
    }

    private fun addScore(points: Int) {
        score += points
        LeaderboardRepository.setLocalBestScore(edition.firebaseKey, score)
    }

    private fun saveHighScoreIfNeeded() {
        LeaderboardRepository.setLocalBestScore(edition.firebaseKey, score)
        val nick = LeaderboardRepository.getSavedNick()
        if (nick.isNotBlank() && score > 0) {
            LeaderboardRepository.submitScore(edition.firebaseKey, nick, score) { _, _ -> }
        }
    }
}
