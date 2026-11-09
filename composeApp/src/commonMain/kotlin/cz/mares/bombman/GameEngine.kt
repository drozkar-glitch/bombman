package cz.mares.bombman

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
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
        description = "Původní DOS klasika: bílý Bomberman, cihlové zdi, balónky Ballom, dálkový odpálovač a tajné dveře.",
        firebaseKey = "bombman_dyna"
    ),
    BOMBIC(
        id = "bombic",
        title = "BOMBÍČ",
        shortTab = "BOMBÍČ",
        year = "2006",
        description = "Česká červíková verze: zahradní bludiště, keře, brouci, berušky a podzemní nora do dalšího kola.",
        firebaseKey = "bombman_bombic"
    ),
    ATOMIC(
        id = "atomic",
        title = "ATOMIC BOMBERMAN",
        shortTab = "ATOMIC",
        year = "1997",
        description = "Arénová bitva proti 3 AI Bombermanům, kteří pokládají bomby a utíkají před výbuchy. Obsahuje kopání bomb!",
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

enum class Difficulty(val label: String, val scoreMult: Int, val speedFactor: Float) {
    EASY("LEHKÁ", 1, 0.85f),
    NORMAL("STŘEDNÍ", 2, 1.0f),
    HARD("TĚŽKÁ", 3, 1.2f)
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
    DETONATOR,
    KICK_BOMB,
    WALL_PASS,
    EXTRA_LIFE
}

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    NONE(0, 0)
}

enum class FlameShape {
    CENTER,
    HORIZ,
    VERT,
    END_UP,
    END_DOWN,
    END_LEFT,
    END_RIGHT
}

enum class EnemyKind(val baseSpeed: Float, val points: Int) {
    BALLOON(0.085f, 100),
    ONIL(0.115f, 200),
    GHOST(0.095f, 400),
    BEETLE(0.105f, 200),
    PONTAN(0.155f, 1000),
    RIVAL_RED(0.125f, 500),
    RIVAL_BLUE(0.125f, 500),
    RIVAL_GOLD(0.135f, 600)
}

data class BombItem(
    var x: Int,
    var y: Int,
    var ticksLeft: Int,
    val range: Int,
    val ownerIsPlayer: Boolean,
    var playerInside: Boolean = true,
    var slideDir: Direction = Direction.NONE
)

data class FlameCell(
    val x: Int,
    val y: Int,
    val shape: FlameShape,
    var ticksLeft: Int
)

data class CrumblingWall(
    val x: Int,
    val y: Int,
    var ticksLeft: Int
)

data class ScorePopup(
    val x: Float,
    val y: Float,
    val text: String,
    var ticksLeft: Int
)

data class EnemyActor(
    val id: Int,
    var posX: Float,
    var posY: Float,
    val kind: EnemyKind,
    var dir: Direction,
    var bombCooldown: Int = 0,
    var alive: Boolean = true,
    var dyingTicks: Int = 0
) {
    val gridX: Int get() = posX.roundToInt()
    val gridY: Int get() = posY.roundToInt()
}

class GameEngine {
    val gridWidth = 15
    val gridHeight = 11

    var edition by mutableStateOf(GameEdition.DYNA)
    var difficulty by mutableStateOf(Difficulty.NORMAL)

    var level by mutableStateOf(1)
    var score by mutableStateOf(0)
    var lives by mutableStateOf(3)
    var timeLeftSeconds by mutableStateOf(200)

    // Plynulá sub-pixelová pozice hráče (1.0f = střed políčka [1, 1])
    var playerPosX by mutableStateOf(1f)
    var playerPosY by mutableStateOf(1f)
    val playerX: Int get() = playerPosX.roundToInt()
    val playerY: Int get() = playerPosY.roundToInt()

    var playerFacing by mutableStateOf(Direction.DOWN)
    var heldDirection by mutableStateOf(Direction.NONE)
    var playerWalkFrame by mutableStateOf(0)
    var playerInvulnerableTicks by mutableStateOf(0)
    var playerDyingTicks by mutableStateOf(0)

    var maxBombs by mutableStateOf(1)
    var fireRange by mutableStateOf(1)
    var speedLevel by mutableStateOf(1)
    var hasDetonator by mutableStateOf(false)
    var hasKick by mutableStateOf(false)
    var hasWallPass by mutableStateOf(false)

    var stageIntroTicks by mutableStateOf(0)
    var isGameOver by mutableStateOf(false)
    var isVictoryBanner by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var statusMessage by mutableStateOf("Znič všechny příšerky a najdi dveře!")

    var exitX by mutableStateOf(-1)
    var exitY by mutableStateOf(-1)
    var exitRevealed by mutableStateOf(false)

    var renderTick by mutableStateOf(0)

    private val tiles = Array(gridWidth) { Array(gridHeight) { TileType.EMPTY } }
    private val hiddenPowerUps = Array(gridWidth) { Array(gridHeight) { PowerUpType.NONE } }
    private val visiblePowerUps = Array(gridWidth) { Array(gridHeight) { PowerUpType.NONE } }

    val bombs = mutableListOf<BombItem>()
    val flames = mutableListOf<FlameCell>()
    val crumblingWalls = mutableListOf<CrumblingWall>()
    val scorePopups = mutableListOf<ScorePopup>()
    val enemies = mutableListOf<EnemyActor>()

    private var subTickCounter = 0
    private var nextEnemyId = 1
    private var suddenDeathIndex = 0
    private var doorPenaltyCooldown = 0

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
        fireRange = if (edition == GameEdition.MRBOOM || edition == GameEdition.ATOMIC) 2 else 1
        speedLevel = 1
        hasDetonator = false
        hasKick = (edition == GameEdition.ATOMIC)
        hasWallPass = false
        isGameOver = false
        isVictoryBanner = false
        isPaused = false
        setupLevel()
    }

    fun setupLevel() {
        bombs.clear()
        flames.clear()
        crumblingWalls.clear()
        scorePopups.clear()
        enemies.clear()

        playerPosX = 1f
        playerPosY = 1f
        playerFacing = Direction.DOWN
        heldDirection = Direction.NONE
        playerWalkFrame = 0
        playerInvulnerableTicks = 45
        playerDyingTicks = 0
        stageIntroTicks = 28

        subTickCounter = 0
        suddenDeathIndex = 0
        doorPenaltyCooldown = 0
        exitRevealed = false
        exitX = -1
        exitY = -1
        timeLeftSeconds = if (edition == GameEdition.MRBOOM) 120 else 200

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
            GameEdition.DYNA -> 0.44f
            GameEdition.BOMBIC -> 0.48f
            GameEdition.ATOMIC -> 0.46f
            GameEdition.MRBOOM -> 0.42f
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

        // V původním Dyna Blasteru je v každém kole hlavní bonus + případně bonus navíc
        val stagePowerUps = mutableListOf<PowerUpType>()
        when (level % 5) {
            1 -> {
                stagePowerUps.add(PowerUpType.FIRE_UP)
                stagePowerUps.add(PowerUpType.EXTRA_BOMB)
            }
            2 -> {
                stagePowerUps.add(PowerUpType.EXTRA_BOMB)
                stagePowerUps.add(PowerUpType.DETONATOR)
            }
            3 -> {
                stagePowerUps.add(PowerUpType.SPEED_UP)
                stagePowerUps.add(PowerUpType.FIRE_UP)
            }
            4 -> {
                stagePowerUps.add(PowerUpType.WALL_PASS)
                stagePowerUps.add(PowerUpType.KICK_BOMB)
            }
            else -> {
                stagePowerUps.add(PowerUpType.EXTRA_LIFE)
                stagePowerUps.add(PowerUpType.DETONATOR)
            }
        }
        if (edition == GameEdition.ATOMIC || edition == GameEdition.MRBOOM) {
            stagePowerUps.add(PowerUpType.EXTRA_BOMB)
            stagePowerUps.add(PowerUpType.FIRE_UP)
            stagePowerUps.add(PowerUpType.KICK_BOMB)
        }

        for (pu in stagePowerUps) {
            if (candidateSoftWalls.isEmpty()) break
            val pos = candidateSoftWalls.removeAt(0)
            hiddenPowerUps[pos.first][pos.second] = pu
        }

        spawnEnemiesForEdition()
        statusMessage = "STAGE $level"
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
                            posX = c.first.toFloat(),
                            posY = c.second.toFloat(),
                            kind = rivalKinds[i],
                            dir = Direction.LEFT
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
                            posX = c.first.toFloat(),
                            posY = c.second.toFloat(),
                            kind = rivalKinds[i],
                            dir = Direction.LEFT
                        )
                    )
                }
                spawnMonsters(3 + level.coerceAtMost(4), EnemyKind.BALLOON, EnemyKind.ONIL)
            }
            GameEdition.BOMBIC -> {
                val count = 4 + level + difficulty.ordinal
                spawnMonsters(count, EnemyKind.BEETLE, EnemyKind.GHOST)
            }
            GameEdition.DYNA -> {
                val count = 4 + level + difficulty.ordinal
                val secondary = if (level >= 3) EnemyKind.GHOST else EnemyKind.ONIL
                spawnMonsters(count, EnemyKind.BALLOON, secondary)
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
                    posX = pos.first.toFloat(),
                    posY = pos.second.toFloat(),
                    kind = kind,
                    dir = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT).random()
                )
            )
        }
    }

    // Voláno při krátkém ťuknutí nebo držení směrového kříže
    fun movePlayer(dir: Direction) {
        if (dir == Direction.NONE) return
        playerFacing = dir
        stepPlayerInDirection(dir, 0.25f)
        renderTick++
    }

    private fun canPlayerEnterCell(cx: Int, cy: Int): Boolean {
        if (cx !in 1 until gridWidth - 1 || cy !in 1 until gridHeight - 1) return false
        val t = tiles[cx][cy]
        if (t == TileType.HARD_WALL) return false
        if (t == TileType.SOFT_WALL && !hasWallPass) return false

        val bomb = bombs.firstOrNull { it.x == cx && it.y == cy }
        if (bomb != null && !bomb.playerInside) {
            return false
        }
        return true
    }

    private fun stepPlayerInDirection(dir: Direction, stepDist: Float) {
        if (isGameOver || isPaused || isVictoryBanner || stageIntroTicks > 0 || playerDyingTicks > 0) return

        playerFacing = dir
        playerWalkFrame = (playerWalkFrame + 1) % 12

        val curGridX = playerPosX.roundToInt()
        val curGridY = playerPosY.roundToInt()

        // Kontrola kopnutí bomby, pokud do ní hráč tlačí zpříma
        val targetCellX = curGridX + dir.dx
        val targetCellY = curGridY + dir.dy
        val blockingBomb = bombs.firstOrNull { it.x == targetCellX && it.y == targetCellY && !it.playerInside }
        if (blockingBomb != null && hasKick) {
            val distToBomb = abs((playerPosX + dir.dx * stepDist) - targetCellX) + abs((playerPosY + dir.dy * stepDist) - targetCellY)
            if (distToBomb < 0.85f && canBombSlideTo(blockingBomb.x + dir.dx, blockingBomb.y + dir.dy)) {
                blockingBomb.slideDir = dir
                LeaderboardRepository.play(SoundEffect.KICK_BOMB)
                return
            }
        }

        if (dir.dx != 0) {
            // Horizontální pohyb + automatické sklouznutí kolem rohu ocelového pilíře (Corner Sliding)
            val alignedY = curGridY.toFloat()
            val offsetY = playerPosY - alignedY
            val nextX = playerPosX + dir.dx * stepDist
            val checkCellX = if (dir.dx > 0) (nextX + 0.36f).roundToInt() else (nextX - 0.36f).roundToInt()

            if (abs(offsetY) > 0.04f) {
                // Pokud jsme mírně mimo osu uličky a v cílové uličce je volno, srovnáme hráče do uličky
                if (canPlayerEnterCell(checkCellX, curGridY)) {
                    playerPosY = moveTowards(playerPosY, alignedY, stepDist * 0.95f)
                } else {
                    val altGridY = if (offsetY > 0f) curGridY + 1 else curGridY - 1
                    if (canPlayerEnterCell(checkCellX, altGridY) && abs(playerPosY - altGridY) < 0.48f) {
                        playerPosY = moveTowards(playerPosY, altGridY.toFloat(), stepDist * 0.95f)
                    }
                }
            }

            if (abs(playerPosY - playerPosY.roundToInt()) <= 0.24f) {
                val rowY = playerPosY.roundToInt()
                if (canPlayerEnterCell(checkCellX, rowY)) {
                    playerPosX = nextX.coerceIn(1f, (gridWidth - 2).toFloat())
                    playerPosY = moveTowards(playerPosY, rowY.toFloat(), 0.08f)
                } else {
                    playerPosX = moveTowards(playerPosX, curGridX.toFloat(), stepDist)
                }
            }
        } else if (dir.dy != 0) {
            // Vertikální pohyb + automatické sklouznutí kolem rohu (Corner Sliding)
            val alignedX = curGridX.toFloat()
            val offsetX = playerPosX - alignedX
            val nextY = playerPosY + dir.dy * stepDist
            val checkCellY = if (dir.dy > 0) (nextY + 0.36f).roundToInt() else (nextY - 0.36f).roundToInt()

            if (abs(offsetX) > 0.04f) {
                if (canPlayerEnterCell(curGridX, checkCellY)) {
                    playerPosX = moveTowards(playerPosX, alignedX, stepDist * 0.95f)
                } else {
                    val altGridX = if (offsetX > 0f) curGridX + 1 else curGridX - 1
                    if (canPlayerEnterCell(altGridX, checkCellY) && abs(playerPosX - altGridX) < 0.48f) {
                        playerPosX = moveTowards(playerPosX, altGridX.toFloat(), stepDist * 0.95f)
                    }
                }
            }

            if (abs(playerPosX - playerPosX.roundToInt()) <= 0.24f) {
                val colX = playerPosX.roundToInt()
                if (canPlayerEnterCell(colX, checkCellY)) {
                    playerPosY = nextY.coerceIn(1f, (gridHeight - 2).toFloat())
                    playerPosX = moveTowards(playerPosX, colX.toFloat(), 0.08f)
                } else {
                    playerPosY = moveTowards(playerPosY, curGridY.toFloat(), stepDist)
                }
            }
        }

        // Jakmile hráč odejde z políčka čerstvě položené bomby, stane se z ní pevná překážka
        for (b in bombs) {
            if (b.playerInside) {
                if (abs(playerPosX - b.x) > 0.65f || abs(playerPosY - b.y) > 0.65f) {
                    b.playerInside = false
                }
            }
        }

        val gx = playerX
        val gy = playerY
        val pu = visiblePowerUps[gx][gy]
        if (pu != PowerUpType.NONE && abs(playerPosX - gx) < 0.35f && abs(playerPosY - gy) < 0.35f) {
            visiblePowerUps[gx][gy] = PowerUpType.NONE
            applyPowerUp(pu)
        }

        if (exitRevealed && gx == exitX && gy == exitY && abs(playerPosX - exitX) < 0.32f && abs(playerPosY - exitY) < 0.32f) {
            if (enemies.none { it.alive }) {
                advanceToNextLevel()
            }
        }
    }

    private fun moveTowards(current: Float, target: Float, maxDelta: Float): Float {
        val diff = target - current
        return if (abs(diff) <= maxDelta) target else current + if (diff > 0f) maxDelta else -maxDelta
    }

    private fun applyPowerUp(pu: PowerUpType) {
        LeaderboardRepository.play(SoundEffect.POWERUP)
        addScore(200 * difficulty.scoreMult)
        when (pu) {
            PowerUpType.EXTRA_BOMB -> {
                maxBombs = (maxBombs + 1).coerceAtMost(8)
                statusMessage = "BONUS: +1 BOMBA (CELKEM $maxBombs)"
            }
            PowerUpType.FIRE_UP -> {
                fireRange = (fireRange + 1).coerceAtMost(8)
                statusMessage = "BONUS: PLAMEN +1 (DOSAH $fireRange)"
            }
            PowerUpType.SPEED_UP -> {
                speedLevel = (speedLevel + 1).coerceAtMost(4)
                statusMessage = "BONUS: RYCHLÉ BRUSLE!"
            }
            PowerUpType.DETONATOR -> {
                hasDetonator = true
                statusMessage = "BONUS: DÁLKOVÝ ODPÁLOVAČ (TLAČÍTKO B)!"
            }
            PowerUpType.KICK_BOMB -> {
                hasKick = true
                statusMessage = "BONUS: KOPÁNÍ BOMB!"
            }
            PowerUpType.WALL_PASS -> {
                hasWallPass = true
                statusMessage = "BONUS: PRŮCHOD CIHLOVOU ZDÍ!"
            }
            PowerUpType.EXTRA_LIFE -> {
                lives = (lives + 1).coerceAtMost(9)
                statusMessage = "BONUS: EXTRA ŽIVOT (LEFT $lives)!"
            }
            PowerUpType.NONE -> {}
        }
    }

    fun placeBomb() {
        if (isGameOver || isPaused || isVictoryBanner || stageIntroTicks > 0 || playerDyingTicks > 0) return
        val activePlayerBombs = bombs.count { it.ownerIsPlayer }
        if (activePlayerBombs >= maxBombs) return
        val gx = playerX
        val gy = playerY
        if (tiles[gx][gy] != TileType.EMPTY) return
        if (bombs.any { it.x == gx && it.y == gy }) return

        bombs.add(
            BombItem(
                x = gx,
                y = gy,
                ticksLeft = if (hasDetonator) 140 else 46,
                range = fireRange,
                ownerIsPlayer = true,
                playerInside = true
            )
        )
        LeaderboardRepository.play(SoundEffect.PLACE_BOMB)
        renderTick++
    }

    // Tlačítko B: Dálkový odpal nejstarší bomby hráče
    fun detonateOldestBomb() {
        if (isGameOver || isPaused || isVictoryBanner || stageIntroTicks > 0 || playerDyingTicks > 0) return
        if (!hasDetonator) {
            statusMessage = "Pro dálkový odpal najdi bonus DETONÁTOR!"
            return
        }
        val oldest = bombs.firstOrNull { it.ownerIsPlayer }
        if (oldest != null) {
            oldest.ticksLeft = 1
            renderTick++
        }
    }

    fun tick() {
        if (isGameOver || isPaused) return

        if (stageIntroTicks > 0) {
            stageIntroTicks--
            if (stageIntroTicks == 0) {
                statusMessage = when (edition) {
                    GameEdition.DYNA -> "Znič příšerky a najdi tajné dveře!"
                    GameEdition.BOMBIC -> "Vyčisti zahradu a vlez do nory!"
                    GameEdition.ATOMIC -> "Poraz všechny 3 soupeře Bombermanů!"
                    GameEdition.MRBOOM -> "Rychlá aréna! Pozor na padající strop!"
                }
            }
            renderTick++
            return
        }

        if (isVictoryBanner) {
            subTickCounter++
            if (subTickCounter >= 34) {
                isVictoryBanner = false
                level++
                setupLevel()
            }
            renderTick++
            return
        }

        if (playerDyingTicks > 0) {
            playerDyingTicks--
            if (playerDyingTicks == 0) {
                finishPlayerDeath()
            }
            renderTick++
            return
        }

        if (playerInvulnerableTicks > 0) playerInvulnerableTicks--
        if (doorPenaltyCooldown > 0) doorPenaltyCooldown--

        // Plynulý pohyb při držení směrového kříže
        if (heldDirection != Direction.NONE) {
            val step = (0.17f + (speedLevel - 1) * 0.035f).coerceAtMost(0.28f)
            stepPlayerInDirection(heldDirection, step)
        }

        subTickCounter++
        if (subTickCounter % 18 == 0) {
            if (timeLeftSeconds > 0) {
                timeLeftSeconds--
                if (edition == GameEdition.MRBOOM && timeLeftSeconds <= 35 && timeLeftSeconds % 2 == 0) {
                    triggerSuddenDeathBlock()
                }
                if (timeLeftSeconds == 0) {
                    // V původním Dyna Blasteru po vypršení času vyběhnou rychlé mince Pontan
                    spawnDoorPenaltyEnemies(if (exitX > 0) exitX else gridWidth - 2, if (exitY > 0) exitY else gridHeight - 2, 3)
                    statusMessage = "TIME UP! POZOR NA RYCHLÉ POTVORY!"
                }
            }
        }

        updateSlidingBombs()
        updateBombsAndFlames()
        updateCrumblingWallsAndPopups()
        updateEnemies()
        checkPlayerHitByEnemyOrFlame()

        if ((edition == GameEdition.ATOMIC || edition == GameEdition.MRBOOM) && enemies.none { it.alive || it.dyingTicks > 0 }) {
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
        if (enemies.any { it.alive && it.gridX == x && it.gridY == y }) return false
        return true
    }

    private fun updateSlidingBombs() {
        for (b in bombs) {
            if (b.slideDir != Direction.NONE) {
                val nx = b.x + b.slideDir.dx
                val ny = b.y + b.slideDir.dy
                if (canBombSlideTo(nx, ny)) {
                    b.x = nx
                    b.y = ny
                } else {
                    b.slideDir = Direction.NONE
                }
            }
        }
    }

    private fun updateCrumblingWallsAndPopups() {
        val cwIter = crumblingWalls.iterator()
        while (cwIter.hasNext()) {
            val cw = cwIter.next()
            cw.ticksLeft--
            if (cw.ticksLeft <= 0) cwIter.remove()
        }

        val spIter = scorePopups.iterator()
        while (spIter.hasNext()) {
            val sp = spIter.next()
            sp.ticksLeft--
            if (sp.ticksLeft <= 0) spIter.remove()
        }
    }

    private fun updateBombsAndFlames() {
        val fIter = flames.iterator()
        while (fIter.hasNext()) {
            val f = fIter.next()
            f.ticksLeft--
            if (f.ticksLeft <= 0) fIter.remove()
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
        addFlame(bomb.x, bomb.y, FlameShape.CENTER)

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
                    crumblingWalls.add(CrumblingWall(tx, ty, ticksLeft = 10))
                    addScore(10 * difficulty.scoreMult)
                    if (tx == exitX && ty == exitY) {
                        exitRevealed = true
                        statusMessage = "Našel jsi dveře! Znič zbylé příšerky!"
                    } else if (hiddenPowerUps[tx][ty] != PowerUpType.NONE) {
                        visiblePowerUps[tx][ty] = hiddenPowerUps[tx][ty]
                        hiddenPowerUps[tx][ty] = PowerUpType.NONE
                    }
                    break
                } else {
                    // Klasické pravidlo: plamen odpálí ležící odkrytý bonus nebo vyprovokuje dveře!
                    if (visiblePowerUps[tx][ty] != PowerUpType.NONE) {
                        visiblePowerUps[tx][ty] = PowerUpType.NONE
                    }
                    if (exitRevealed && tx == exitX && ty == exitY && doorPenaltyCooldown == 0) {
                        doorPenaltyCooldown = 30
                        spawnDoorPenaltyEnemies(exitX, exitY, 2)
                        statusMessage = "Pozor! Odpálil jsi dveře – vyběhly potvory!"
                    }

                    val nextX = tx + d.dx
                    val nextY = ty + d.dy
                    val blockedNext = step == bomb.range ||
                        getTile(nextX, nextY) != TileType.EMPTY

                    val shape = if (blockedNext) {
                        when (d) {
                            Direction.UP -> FlameShape.END_UP
                            Direction.DOWN -> FlameShape.END_DOWN
                            Direction.LEFT -> FlameShape.END_LEFT
                            Direction.RIGHT -> FlameShape.END_RIGHT
                            Direction.NONE -> FlameShape.CENTER
                        }
                    } else {
                        if (d.dx != 0) FlameShape.HORIZ else FlameShape.VERT
                    }
                    addFlame(tx, ty, shape)
                }
            }
        }
    }

    private fun spawnDoorPenaltyEnemies(sx: Int, sy: Int, count: Int) {
        val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)
        for (i in 0 until count) {
            enemies.add(
                EnemyActor(
                    id = nextEnemyId++,
                    posX = sx.toFloat(),
                    posY = sy.toFloat(),
                    kind = EnemyKind.PONTAN,
                    dir = dirs[i % dirs.size]
                )
            )
        }
    }

    private fun addFlame(x: Int, y: Int, shape: FlameShape) {
        val existing = flames.firstOrNull { it.x == x && it.y == y }
        if (existing != null) {
            flames.remove(existing)
            flames.add(FlameCell(x = x, y = y, shape = FlameShape.CENTER, ticksLeft = 11))
        } else {
            flames.add(FlameCell(x = x, y = y, shape = shape, ticksLeft = 11))
        }
    }

    private fun updateEnemies() {
        for (e in enemies) {
            if (!e.alive) {
                if (e.dyingTicks > 0) e.dyingTicks--
                continue
            }

            val gx = e.gridX
            val gy = e.gridY

            if (flames.any { it.x == gx && it.y == gy }) {
                e.alive = false
                e.dyingTicks = 14
                val pts = e.kind.points * difficulty.scoreMult
                addScore(pts)
                scorePopups.add(ScorePopup(x = e.posX, y = e.posY, text = "$pts", ticksLeft = 18))
                LeaderboardRepository.play(SoundEffect.ENEMY_KILL)
                val remaining = enemies.count { it.alive }
                statusMessage = if (remaining == 0) {
                    if (edition == GameEdition.DYNA || edition == GameEdition.BOMBIC) {
                        "Všechny příšerky zničeny! Vejdi do dveří!"
                    } else {
                        "Aréna vyčištěna!"
                    }
                } else {
                    "Zásah +$pts! Zbývá nepřátel: $remaining"
                }
                continue
            }

            if (e.bombCooldown > 0) e.bombCooldown--

            val isRival = e.kind in listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE, EnemyKind.RIVAL_GOLD)
            val canPassSoft = (e.kind == EnemyKind.GHOST || e.kind == EnemyKind.PONTAN)
            val step = e.kind.baseSpeed * difficulty.speedFactor

            val distToCenter = abs(e.posX - gx) + abs(e.posY - gy)
            if (distToCenter <= step * 0.85f) {
                e.posX = gx.toFloat()
                e.posY = gy.toFloat()

                val possibleDirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT).filter { d ->
                    val nx = gx + d.dx
                    val ny = gy + d.dy
                    val inBounds = nx in 1 until gridWidth - 1 && ny in 1 until gridHeight - 1
                    val tileOk = if (canPassSoft) tiles[nx][ny] != TileType.HARD_WALL else tiles[nx][ny] == TileType.EMPTY
                    val noBomb = bombs.none { it.x == nx && it.y == ny }
                    val noFlame = flames.none { it.x == nx && it.y == ny }
                    inBounds && tileOk && noBomb && noFlame
                }

                if (possibleDirs.isNotEmpty()) {
                    e.dir = if (isRival) {
                        val safe = possibleDirs.filter { d -> !isTileInDanger(gx + d.dx, gy + d.dy) }
                        val pool = if (safe.isNotEmpty()) safe else possibleDirs
                        pool.minByOrNull { d ->
                            abs((gx + d.dx) - playerX) + abs((gy + d.dy) - playerY) + Random.nextInt(2)
                        } ?: pool.first()
                    } else if (e.kind == EnemyKind.ONIL || e.kind == EnemyKind.PONTAN) {
                        if (Random.nextFloat() < 0.55f) {
                            possibleDirs.minByOrNull { d ->
                                abs((gx + d.dx) - playerX) + abs((gy + d.dy) - playerY)
                            } ?: possibleDirs.first()
                        } else {
                            possibleDirs.random()
                        }
                    } else {
                        if (e.dir in possibleDirs && Random.nextFloat() < 0.72f) e.dir else possibleDirs.random()
                    }
                } else {
                    e.dir = Direction.NONE
                }

                if (isRival) {
                    if (visiblePowerUps[gx][gy] != PowerUpType.NONE) {
                        visiblePowerUps[gx][gy] = PowerUpType.NONE
                    }
                    val nearTarget = (abs(gx - playerX) + abs(gy - playerY) <= 2) ||
                        listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT).any { d ->
                            getTile(gx + d.dx, gy + d.dy) == TileType.SOFT_WALL
                        }
                    if (nearTarget && e.bombCooldown == 0 && bombs.none { it.x == gx && it.y == gy }) {
                        if (Random.nextFloat() < 0.35f) {
                            bombs.add(
                                BombItem(
                                    x = gx,
                                    y = gy,
                                    ticksLeft = 46,
                                    range = 2,
                                    ownerIsPlayer = false,
                                    playerInside = false
                                )
                            )
                            e.bombCooldown = 60
                        }
                    }
                }
            }

            if (e.dir != Direction.NONE) {
                val targetX = gx + e.dir.dx
                val targetY = gy + e.dir.dy
                val tileOk = if (canPassSoft) getTile(targetX, targetY) != TileType.HARD_WALL else getTile(targetX, targetY) == TileType.EMPTY
                val noBomb = bombs.none { it.x == targetX && it.y == targetY }
                if (tileOk && noBomb) {
                    e.posX += e.dir.dx * step
                    e.posY += e.dir.dy * step
                } else {
                    e.posX = gx.toFloat()
                    e.posY = gy.toFloat()
                }
            }
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
        if (playerInvulnerableTicks > 0 || playerDyingTicks > 0 || isGameOver || isVictoryBanner || stageIntroTicks > 0) return

        val gx = playerX
        val gy = playerY
        val hitFlame = flames.any { it.x == gx && it.y == gy }
        val hitEnemy = enemies.any {
            it.alive &&
                it.kind !in listOf(EnemyKind.RIVAL_RED, EnemyKind.RIVAL_BLUE, EnemyKind.RIVAL_GOLD) &&
                abs(it.posX - playerPosX) < 0.62f &&
                abs(it.posY - playerPosY) < 0.62f
        }

        if (hitFlame || hitEnemy) {
            LeaderboardRepository.play(SoundEffect.PLAYER_DIE)
            heldDirection = Direction.NONE
            playerDyingTicks = 22
            statusMessage = if (hitFlame) "Zasáhl tě výbuch bomby!" else "Chytila tě příšerka!"
        }
    }

    private fun finishPlayerDeath() {
        lives--
        hasDetonator = false
        hasWallPass = false
        if (lives <= 0) {
            lives = 0
            isGameOver = true
            statusMessage = "GAME OVER"
            saveHighScoreIfNeeded()
        } else {
            bombs.clear()
            flames.clear()
            playerPosX = 1f
            playerPosY = 1f
            playerFacing = Direction.DOWN
            playerInvulnerableTicks = 50
            statusMessage = "Ztratil jsi život! Zbývá: $lives"
        }
    }

    private fun advanceToNextLevel() {
        LeaderboardRepository.play(SoundEffect.LEVEL_CLEAR)
        val timeBonus = (timeLeftSeconds * 5 * difficulty.scoreMult).coerceAtLeast(100)
        addScore(500 * difficulty.scoreMult + timeBonus)
        isVictoryBanner = true
        subTickCounter = 0
        statusMessage = "STAGE $level CLEAR! BONUS +$timeBonus"
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
