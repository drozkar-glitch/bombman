package cz.mares.bombman

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun BombManBoard(
    engine: GameEngine,
    modifier: Modifier = Modifier
) {
    val tick = engine.renderTick
    val targetRatio = engine.gridWidth.toFloat() / engine.gridHeight.toFloat()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = maxHeight
        val currentRatio = if (availH.value > 0f) availW.value / availH.value else targetRatio

        val boardW = if (currentRatio > targetRatio) availH * targetRatio else availW
        val boardH = if (currentRatio > targetRatio) availH else availW / targetRatio

        Box(
            modifier = Modifier
                .width(boardW)
                .height(boardH)
                .border(2.dp, Color(0xFF888888))
                .background(Color(0xFF000000))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (tick < 0) return@Canvas

                val cw = size.width / engine.gridWidth
                val ch = size.height / engine.gridHeight

                // 1. Podlaha + stíny pod zdmi (přesně jako v DOS Dyna Blasteru)
                for (gx in 0 until engine.gridWidth) {
                    for (gy in 0 until engine.gridHeight) {
                        val left = gx * cw
                        val top = gy * ch
                        val hasWallAbove = gy > 0 && engine.getTile(gx, gy - 1) != TileType.EMPTY
                        drawRetroFloor(engine.edition, gx, gy, left, top, cw, ch, hasWallAbove)
                    }
                }

                // 2. Nerozbitné pilíře, cihlové zdi, dveře a bonusy
                for (gx in 0 until engine.gridWidth) {
                    for (gy in 0 until engine.gridHeight) {
                        val left = gx * cw
                        val top = gy * ch

                        when (engine.getTile(gx, gy)) {
                            TileType.HARD_WALL -> drawRetroHardWall(engine.edition, gx, gy, engine.gridWidth, engine.gridHeight, left, top, cw, ch)
                            TileType.SOFT_WALL -> drawRetroSoftWall(engine.edition, left, top, cw, ch, 0)
                            TileType.EMPTY -> {
                                if (engine.exitRevealed && gx == engine.exitX && gy == engine.exitY) {
                                    val allDead = engine.enemies.none { it.alive }
                                    drawRetroExitDoor(engine.edition, left, top, cw, ch, allDead, tick)
                                }
                                val pu = engine.getVisiblePowerUp(gx, gy)
                                if (pu != PowerUpType.NONE) {
                                    drawRetroPowerUp(pu, left, top, cw, ch, tick)
                                }
                            }
                        }
                    }
                }

                // 3. Rozpadající se cihlové zdi po zásahu plamenem
                for (cr in engine.crumblingWalls) {
                    val phase = (10 - cr.ticksLeft).coerceIn(1, 3)
                    drawRetroSoftWall(engine.edition, cr.x * cw, cr.y * ch, cw, ch, phase)
                }

                // 4. Položené dýchající pixelové bomby
                for (b in engine.bombs) {
                    drawRetroBomb(b.x * cw, b.y * ch, cw, ch, b.ticksLeft)
                }

                // 5. Spojené křížové plameny výbuchu
                for (f in engine.flames) {
                    drawRetroFlame(f.x * cw, f.y * ch, cw, ch, f.shape, f.ticksLeft)
                }

                // 6. Nepřátelé (plynulá sub-pixelová pozice)
                for (e in engine.enemies) {
                    if (e.alive || e.dyingTicks > 0) {
                        drawRetroEnemy(e, e.posX * cw, e.posY * ch, cw, ch, tick)
                    }
                }

                // 7. Hráč (plynulá sub-pixelová pozice + animace chůze nebo smrti)
                if (!engine.isGameOver) {
                    if (engine.playerDyingTicks > 0) {
                        drawDyingBomberman(engine.playerPosX * cw, engine.playerPosY * ch, cw, ch, engine.playerDyingTicks)
                    } else {
                        val blinkHide = engine.playerInvulnerableTicks > 0 && (engine.playerInvulnerableTicks % 4 < 2)
                        if (!blinkHide) {
                            if (engine.edition == GameEdition.BOMBIC) {
                                drawRetroWorm(
                                    left = engine.playerPosX * cw,
                                    top = engine.playerPosY * ch,
                                    cw = cw,
                                    ch = ch,
                                    facing = engine.playerFacing,
                                    walkFrame = engine.playerWalkFrame
                                )
                            } else {
                                val suitCol = when (engine.edition) {
                                    GameEdition.DYNA -> Color(0xFF0058F8)
                                    GameEdition.ATOMIC -> Color(0xFF00A844)
                                    GameEdition.MRBOOM -> Color(0xFF9838FC)
                                    GameEdition.BOMBIC -> Color(0xFF0058F8)
                                }
                                drawPixelBomberman(
                                    left = engine.playerPosX * cw,
                                    top = engine.playerPosY * ch,
                                    cw = cw,
                                    ch = ch,
                                    helmetColor = Color(0xFFF8F8F8),
                                    suitColor = suitCol,
                                    facing = engine.playerFacing,
                                    walkFrame = engine.playerWalkFrame,
                                    isMoving = engine.heldDirection != Direction.NONE
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.pxRect(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    gx: Int,
    gy: Int,
    gw: Int,
    gh: Int,
    color: Color
) {
    val ux = cw / 16f
    val uy = ch / 16f
    drawRect(
        color = color,
        topLeft = Offset(left + gx * ux, top + gy * uy),
        size = Size(gw * ux + 0.4f, gh * uy + 0.4f)
    )
}

private fun DrawScope.drawRetroFloor(
    edition: GameEdition,
    gx: Int,
    gy: Int,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    hasWallAbove: Boolean
) {
    val baseGreen = when (edition) {
        GameEdition.DYNA -> Color(0xFF187818)
        GameEdition.BOMBIC -> Color(0xFF287010)
        GameEdition.ATOMIC -> Color(0xFF245848)
        GameEdition.MRBOOM -> Color(0xFF205068)
    }
    val shadowGreen = when (edition) {
        GameEdition.DYNA -> Color(0xFF0C480C)
        GameEdition.BOMBIC -> Color(0xFF184808)
        GameEdition.ATOMIC -> Color(0xFF143428)
        GameEdition.MRBOOM -> Color(0xFF102C3C)
    }
    drawRect(color = baseGreen, topLeft = Offset(left, top), size = Size(cw + 0.6f, ch + 0.6f))
    if (hasWallAbove && gx in 1..13 && gy in 1..9) {
        drawRect(color = shadowGreen, topLeft = Offset(left, top), size = Size(cw + 0.6f, ch * 0.22f))
    }
}

private fun DrawScope.drawRetroHardWall(
    edition: GameEdition,
    gx: Int,
    gy: Int,
    gridW: Int,
    gridH: Int,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float
) {
    val isBorder = (gx == 0 || gy == 0 || gx == gridW - 1 || gy == gridH - 1)
    if (edition == GameEdition.BOMBIC && !isBorder) {
        // Kamenný balvan v zahradě
        pxRect(left, top, cw, ch, 0, 0, 16, 16, Color(0xFF183008))
        pxRect(left, top, cw, ch, 1, 1, 14, 14, Color(0xFF687078))
        pxRect(left, top, cw, ch, 2, 2, 11, 11, Color(0xFF98A0A8))
        pxRect(left, top, cw, ch, 3, 3, 6, 4, Color(0xFFD0D8E0))
        return
    }

    // Klasický 3D zkosený ocelový kvádr z Dyna Blasteru
    val darkEdge = Color(0xFF181818)
    val shadowGray = Color(0xFF585858)
    val midGray = if (isBorder) Color(0xFF888888) else Color(0xFFA0A0A0)
    val lightGray = Color(0xFFE8E8E8)

    pxRect(left, top, cw, ch, 0, 0, 16, 16, darkEdge)
    pxRect(left, top, cw, ch, 1, 1, 14, 14, shadowGray)
    pxRect(left, top, cw, ch, 1, 1, 13, 13, lightGray)
    pxRect(left, top, cw, ch, 3, 3, 10, 10, midGray)

    if (!isBorder) {
        // 4 rohové nýty na vnitřních pilířích
        pxRect(left, top, cw, ch, 4, 4, 2, 2, darkEdge)
        pxRect(left, top, cw, ch, 10, 4, 2, 2, darkEdge)
        pxRect(left, top, cw, ch, 4, 10, 2, 2, darkEdge)
        pxRect(left, top, cw, ch, 10, 10, 2, 2, darkEdge)
    }
}

private fun DrawScope.drawRetroSoftWall(
    edition: GameEdition,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    crumblePhase: Int
) {
    if (crumblePhase > 0) {
        // Rozpadající se hořící cihla
        val fireCol = if (crumblePhase % 2 == 1) Color(0xFFF85800) else Color(0xFFF8B800)
        val ashCol = Color(0xFF582000)
        pxRect(left, top, cw, ch, 2, 2, 5, 4, fireCol)
        pxRect(left, top, cw, ch, 9, 3, 5, 4, ashCol)
        pxRect(left, top, cw, ch, 4, 9, 6, 4, fireCol)
        pxRect(left, top, cw, ch, 1, 11, 4, 3, ashCol)
        return
    }

    if (edition == GameEdition.BOMBIC) {
        // Zelený keř v Bombíčovi
        pxRect(left, top, cw, ch, 0, 0, 16, 16, Color(0xFF0C3808))
        pxRect(left, top, cw, ch, 1, 1, 14, 14, Color(0xFF187818))
        pxRect(left, top, cw, ch, 2, 2, 5, 5, Color(0xFF48C828))
        pxRect(left, top, cw, ch, 8, 3, 5, 5, Color(0xFF48C828))
        pxRect(left, top, cw, ch, 4, 8, 6, 5, Color(0xFF38A820))
        return
    }

    // Klasická cihlová zeď z Dyna Blasteru (4 řady cihel se světlou maltou a stínem)
    val mortar = Color(0xFFD8B890)
    val brickMain = if (edition == GameEdition.DYNA) Color(0xFFA83808) else Color(0xFFB85010)
    val brickDark = Color(0xFF581800)
    val brickHi = Color(0xFFD86828)

    pxRect(left, top, cw, ch, 0, 0, 16, 16, brickDark)

    // Řada 1
    pxRect(left, top, cw, ch, 0, 0, 7, 3, brickMain)
    pxRect(left, top, cw, ch, 0, 0, 6, 1, brickHi)
    pxRect(left, top, cw, ch, 8, 0, 8, 3, brickMain)
    pxRect(left, top, cw, ch, 8, 0, 7, 1, brickHi)
    pxRect(left, top, cw, ch, 7, 0, 1, 3, mortar)
    pxRect(left, top, cw, ch, 0, 3, 16, 1, mortar)

    // Řada 2
    pxRect(left, top, cw, ch, 0, 4, 3, 3, brickMain)
    pxRect(left, top, cw, ch, 4, 4, 8, 3, brickMain)
    pxRect(left, top, cw, ch, 4, 4, 7, 1, brickHi)
    pxRect(left, top, cw, ch, 13, 4, 3, 3, brickMain)
    pxRect(left, top, cw, ch, 3, 4, 1, 3, mortar)
    pxRect(left, top, cw, ch, 12, 4, 1, 3, mortar)
    pxRect(left, top, cw, ch, 0, 7, 16, 1, mortar)

    // Řada 3
    pxRect(left, top, cw, ch, 0, 8, 7, 3, brickMain)
    pxRect(left, top, cw, ch, 0, 8, 6, 1, brickHi)
    pxRect(left, top, cw, ch, 8, 8, 8, 3, brickMain)
    pxRect(left, top, cw, ch, 8, 8, 7, 1, brickHi)
    pxRect(left, top, cw, ch, 7, 8, 1, 3, mortar)
    pxRect(left, top, cw, ch, 0, 11, 16, 1, mortar)

    // Řada 4
    pxRect(left, top, cw, ch, 0, 12, 3, 3, brickMain)
    pxRect(left, top, cw, ch, 4, 12, 8, 3, brickMain)
    pxRect(left, top, cw, ch, 4, 12, 7, 1, brickHi)
    pxRect(left, top, cw, ch, 13, 12, 3, 3, brickMain)
    pxRect(left, top, cw, ch, 3, 12, 1, 3, mortar)
    pxRect(left, top, cw, ch, 12, 12, 1, 3, mortar)
    pxRect(left, top, cw, ch, 0, 15, 16, 1, mortar)
}

private fun DrawScope.drawRetroExitDoor(
    edition: GameEdition,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    unlocked: Boolean,
    tick: Int
) {
    val frameCol = if (unlocked && tick % 6 < 3) Color(0xFFF8D820) else Color(0xFFD88818)
    val innerCol = if (unlocked) Color(0xFF28B838) else Color(0xFF583010)
    if (edition == GameEdition.BOMBIC) {
        pxRect(left, top, cw, ch, 2, 2, 12, 12, frameCol)
        pxRect(left, top, cw, ch, 4, 4, 8, 8, Color(0xFF101010))
    } else {
        pxRect(left, top, cw, ch, 2, 1, 12, 14, Color(0xFF000000))
        pxRect(left, top, cw, ch, 3, 2, 10, 12, frameCol)
        pxRect(left, top, cw, ch, 5, 4, 6, 10, innerCol)
        pxRect(left, top, cw, ch, 9, 8, 1, 2, Color(0xFFF8F8F8))
    }
}

private fun DrawScope.drawRetroPowerUp(
    pu: PowerUpType,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    tick: Int
) {
    val borderCol = if (tick % 6 < 3) Color(0xFFF8F8F8) else Color(0xFFF8B800)
    val bgCol = when (pu) {
        PowerUpType.EXTRA_BOMB -> Color(0xFF1858C8)
        PowerUpType.FIRE_UP -> Color(0xFFD82800)
        PowerUpType.SPEED_UP -> Color(0xFF009838)
        PowerUpType.DETONATOR -> Color(0xFF8820B8)
        PowerUpType.KICK_BOMB -> Color(0xFFD87800)
        PowerUpType.WALL_PASS -> Color(0xFF785838)
        PowerUpType.EXTRA_LIFE -> Color(0xFFD81868)
        PowerUpType.NONE -> Color.Transparent
    }

    // Klasický čtvercový retro panel bonusu
    pxRect(left, top, cw, ch, 1, 1, 14, 14, Color(0xFF000000))
    pxRect(left, top, cw, ch, 2, 2, 12, 12, borderCol)
    pxRect(left, top, cw, ch, 3, 3, 10, 10, bgCol)

    when (pu) {
        PowerUpType.EXTRA_BOMB -> {
            pxRect(left, top, cw, ch, 5, 6, 6, 6, Color.Black)
            pxRect(left, top, cw, ch, 6, 7, 2, 2, Color.White)
            pxRect(left, top, cw, ch, 8, 4, 2, 2, Color(0xFFF8D800))
        }
        PowerUpType.FIRE_UP -> {
            pxRect(left, top, cw, ch, 6, 4, 4, 8, Color(0xFFF8D800))
            pxRect(left, top, cw, ch, 4, 6, 8, 4, Color(0xFFF8D800))
            pxRect(left, top, cw, ch, 7, 6, 2, 4, Color.White)
        }
        PowerUpType.SPEED_UP -> {
            // Kolečková brusle
            pxRect(left, top, cw, ch, 5, 4, 4, 5, Color.White)
            pxRect(left, top, cw, ch, 5, 8, 7, 2, Color.White)
            pxRect(left, top, cw, ch, 5, 10, 2, 2, Color(0xFFF8D800))
            pxRect(left, top, cw, ch, 10, 10, 2, 2, Color(0xFFF8D800))
        }
        PowerUpType.DETONATOR -> {
            // Krabička s pákou odpálovače (Tlačítko B)
            pxRect(left, top, cw, ch, 5, 7, 6, 5, Color(0xFFE0E0E0))
            pxRect(left, top, cw, ch, 7, 5, 2, 2, Color(0xFFE0E0E0))
            pxRect(left, top, cw, ch, 5, 4, 6, 1, Color(0xFFF83800))
        }
        PowerUpType.KICK_BOMB -> {
            // Bota
            pxRect(left, top, cw, ch, 5, 5, 4, 6, Color.White)
            pxRect(left, top, cw, ch, 5, 9, 7, 3, Color.White)
        }
        PowerUpType.WALL_PASS -> {
            pxRect(left, top, cw, ch, 4, 5, 8, 6, Color(0xFFF8B880))
            pxRect(left, top, cw, ch, 6, 7, 4, 4, Color.White)
        }
        PowerUpType.EXTRA_LIFE -> {
            pxRect(left, top, cw, ch, 4, 5, 3, 3, Color.White)
            pxRect(left, top, cw, ch, 9, 5, 3, 3, Color.White)
            pxRect(left, top, cw, ch, 5, 8, 6, 3, Color.White)
            pxRect(left, top, cw, ch, 7, 11, 2, 1, Color.White)
        }
        PowerUpType.NONE -> {}
    }
}

private fun DrawScope.drawRetroBomb(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    ticksLeft: Int
) {
    // Bomba v Dyna Blasteru pulzuje (dýchá) ve 3 velikostech
    val big = (ticksLeft / 4) % 2 == 0
    val bodyCol = if (ticksLeft <= 10 && ticksLeft % 2 == 0) Color(0xFFD82800) else Color(0xFF101820)
    val shineCol = Color(0xFFF8F8F8)

    if (big) {
        pxRect(left, top, cw, ch, 4, 4, 8, 10, bodyCol)
        pxRect(left, top, cw, ch, 3, 5, 10, 8, bodyCol)
        pxRect(left, top, cw, ch, 2, 6, 12, 6, bodyCol)
        pxRect(left, top, cw, ch, 5, 6, 2, 3, shineCol)
        pxRect(left, top, cw, ch, 4, 7, 1, 2, shineCol)
    } else {
        pxRect(left, top, cw, ch, 5, 5, 6, 8, bodyCol)
        pxRect(left, top, cw, ch, 4, 6, 8, 6, bodyCol)
        pxRect(left, top, cw, ch, 3, 7, 10, 4, bodyCol)
        pxRect(left, top, cw, ch, 5, 7, 2, 2, shineCol)
    }

    // Hrdlo a prskající zápalná šňůra
    pxRect(left, top, cw, ch, 7, 3, 2, 2, Color(0xFF787878))
    pxRect(left, top, cw, ch, 8, 2, 2, 1, Color(0xFFD8A040))
    val sparkCol = if (ticksLeft % 2 == 0) Color(0xFFF8F800) else Color(0xFFF83800)
    pxRect(left, top, cw, ch, 10, 1, 3, 2, sparkCol)
}

private fun DrawScope.drawRetroFlame(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    shape: FlameShape,
    ticksLeft: Int
) {
    // 3vrstvý propojený plamen (červený okraj -> oranžovo-žlutý střed -> bílý žár uvnitř)
    val flash = ticksLeft % 2 == 0
    val outer = if (flash) Color(0xFFD82800) else Color(0xFFF85800)
    val mid = if (flash) Color(0xFFF8B800) else Color(0xFFF8D820)
    val core = Color(0xFFF8F8D8)

    when (shape) {
        FlameShape.CENTER -> {
            pxRect(left, top, cw, ch, 0, 2, 16, 12, outer)
            pxRect(left, top, cw, ch, 2, 0, 12, 16, outer)
            pxRect(left, top, cw, ch, 0, 4, 16, 8, mid)
            pxRect(left, top, cw, ch, 4, 0, 8, 16, mid)
            pxRect(left, top, cw, ch, 0, 6, 16, 4, core)
            pxRect(left, top, cw, ch, 6, 0, 4, 16, core)
        }
        FlameShape.HORIZ -> {
            pxRect(left, top, cw, ch, 0, 2, 16, 12, outer)
            pxRect(left, top, cw, ch, 0, 4, 16, 8, mid)
            pxRect(left, top, cw, ch, 0, 6, 16, 4, core)
        }
        FlameShape.VERT -> {
            pxRect(left, top, cw, ch, 2, 0, 12, 16, outer)
            pxRect(left, top, cw, ch, 4, 0, 8, 16, mid)
            pxRect(left, top, cw, ch, 6, 0, 4, 16, core)
        }
        FlameShape.END_LEFT -> {
            pxRect(left, top, cw, ch, 4, 2, 12, 12, outer)
            pxRect(left, top, cw, ch, 2, 4, 14, 8, outer)
            pxRect(left, top, cw, ch, 4, 4, 12, 8, mid)
            pxRect(left, top, cw, ch, 6, 6, 10, 4, core)
        }
        FlameShape.END_RIGHT -> {
            pxRect(left, top, cw, ch, 0, 2, 12, 12, outer)
            pxRect(left, top, cw, ch, 0, 4, 14, 8, outer)
            pxRect(left, top, cw, ch, 0, 4, 12, 8, mid)
            pxRect(left, top, cw, ch, 0, 6, 10, 4, core)
        }
        FlameShape.END_UP -> {
            pxRect(left, top, cw, ch, 2, 4, 12, 12, outer)
            pxRect(left, top, cw, ch, 4, 2, 8, 14, outer)
            pxRect(left, top, cw, ch, 4, 4, 8, 12, mid)
            pxRect(left, top, cw, ch, 6, 6, 4, 10, core)
        }
        FlameShape.END_DOWN -> {
            pxRect(left, top, cw, ch, 2, 0, 12, 12, outer)
            pxRect(left, top, cw, ch, 4, 0, 8, 14, outer)
            pxRect(left, top, cw, ch, 4, 0, 8, 12, mid)
            pxRect(left, top, cw, ch, 6, 0, 4, 10, core)
        }
    }
}

private fun DrawScope.drawPixelBomberman(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    helmetColor: Color,
    suitColor: Color,
    facing: Direction,
    walkFrame: Int,
    isMoving: Boolean
) {
    val black = Color(0xFF080808)
    val pink = Color(0xFFF85898)
    val skin = Color(0xFFF8C890)
    val gold = Color(0xFFF8D800)

    // Růžová kulička anténky na helmě
    pxRect(left, top, cw, ch, 7, 0, 2, 2, pink)

    // Černý obrys helmy a bílá výplň
    pxRect(left, top, cw, ch, 3, 2, 10, 7, black)
    pxRect(left, top, cw, ch, 4, 3, 8, 5, helmetColor)

    // Obličejový průzor a oči podle směru pohledu
    if (facing != Direction.UP) {
        val visorX = when (facing) {
            Direction.LEFT -> 4
            Direction.RIGHT -> 6
            else -> 5
        }
        pxRect(left, top, cw, ch, visorX, 4, 6, 3, skin)
        if (facing == Direction.LEFT) {
            pxRect(left, top, cw, ch, 5, 5, 1, 2, black)
            pxRect(left, top, cw, ch, 8, 5, 1, 2, black)
        } else if (facing == Direction.RIGHT) {
            pxRect(left, top, cw, ch, 7, 5, 1, 2, black)
            pxRect(left, top, cw, ch, 10, 5, 1, 2, black)
        } else {
            pxRect(left, top, cw, ch, 6, 5, 1, 2, black)
            pxRect(left, top, cw, ch, 9, 5, 1, 2, black)
        }
    }

    // Trup s modrou vestou a černým opaskem se zlatou sponou
    pxRect(left, top, cw, ch, 4, 9, 8, 4, suitColor)
    pxRect(left, top, cw, ch, 4, 11, 8, 1, black)
    pxRect(left, top, cw, ch, 7, 11, 2, 1, gold)

    // Růžové ruce a animované boty při chůzi
    val stepCycle = if (isMoving) (walkFrame / 3) % 2 else 0
    val leftHandY = if (isMoving && stepCycle == 0) 9 else 10
    val rightHandY = if (isMoving && stepCycle == 1) 9 else 10
    pxRect(left, top, cw, ch, 2, leftHandY, 2, 2, pink)
    pxRect(left, top, cw, ch, 12, rightHandY, 2, 2, pink)

    val leftFootY = if (isMoving && stepCycle == 1) 12 else 13
    val rightFootY = if (isMoving && stepCycle == 0) 12 else 13
    pxRect(left, top, cw, ch, 4, leftFootY, 3, 3, pink)
    pxRect(left, top, cw, ch, 9, rightFootY, 3, 3, pink)
}

private fun DrawScope.drawDyingBomberman(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    dyingTicks: Int
) {
    // Klasická animace nafouknutí a rozplynutí po zásahu plamenem
    val col = if (dyingTicks % 2 == 0) Color(0xFFF85800) else Color(0xFFF8F8F8)
    pxRect(left, top, cw, ch, 3, 3, 10, 10, col)
    pxRect(left, top, cw, ch, 5, 5, 2, 3, Color.Black)
    pxRect(left, top, cw, ch, 9, 5, 2, 3, Color.Black)
    pxRect(left, top, cw, ch, 6, 10, 4, 2, Color.Black)
}

private fun DrawScope.drawRetroWorm(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    facing: Direction,
    walkFrame: Int
) {
    val wiggle = if ((walkFrame / 3) % 2 == 0) 0 else 1
    pxRect(left, top, cw, ch, 4 - facing.dx * 2, 9 + wiggle, 8, 5, Color(0xFFE868A8))
    pxRect(left, top, cw, ch, 3, 4, 10, 8, Color(0xFFF8C820))
    pxRect(left, top, cw, ch, 5, 5, 2, 3, Color.White)
    pxRect(left, top, cw, ch, 9, 5, 2, 3, Color.White)
    pxRect(left, top, cw, ch, 6, 6, 1, 2, Color.Black)
    pxRect(left, top, cw, ch, 10, 6, 1, 2, Color.Black)
}

private fun DrawScope.drawRetroEnemy(
    enemy: EnemyActor,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    tick: Int
) {
    if (!enemy.alive && enemy.dyingTicks > 0) {
        val flash = if (enemy.dyingTicks % 2 == 0) Color(0xFFF8D800) else Color(0xFFF83800)
        pxRect(left, top, cw, ch, 3, 3, 10, 10, flash)
        return
    }

    val bob = if (tick % 8 < 4) 0 else 1

    when (enemy.kind) {
        EnemyKind.RIVAL_RED -> drawPixelBomberman(left, top, cw, ch, Color(0xFFF83800), Color(0xFF880000), enemy.dir, tick, true)
        EnemyKind.RIVAL_BLUE -> drawPixelBomberman(left, top, cw, ch, Color(0xFF38B8F8), Color(0xFF003888), enemy.dir, tick, true)
        EnemyKind.RIVAL_GOLD -> drawPixelBomberman(left, top, cw, ch, Color(0xFFF8D800), Color(0xFF785800), enemy.dir, tick, true)

        EnemyKind.BALLOON -> {
            // Ikonický oranžový balónek Ballom z 1. levelu Dyna Blasteru
            val orange = Color(0xFFF86818)
            val hi = Color(0xFFF8B880)
            pxRect(left, top, cw, ch, 4, 1 + bob, 8, 11, orange)
            pxRect(left, top, cw, ch, 2, 3 + bob, 12, 8, orange)
            pxRect(left, top, cw, ch, 4, 3 + bob, 2, 2, hi)
            pxRect(left, top, cw, ch, 5, 5 + bob, 2, 3, Color.White)
            pxRect(left, top, cw, ch, 9, 5 + bob, 2, 3, Color.White)
            pxRect(left, top, cw, ch, 6, 6 + bob, 1, 2, Color.Black)
            pxRect(left, top, cw, ch, 10, 6 + bob, 1, 2, Color.Black)
            pxRect(left, top, cw, ch, 6, 12 + bob, 4, 2, Color(0xFFD8B880))
        }

        EnemyKind.ONIL -> {
            // Modrá hranatá příšerka Onil z 2. levelu
            val blue = Color(0xFF2878F8)
            pxRect(left, top, cw, ch, 2, 2 + bob, 12, 11, blue)
            pxRect(left, top, cw, ch, 4, 4 + bob, 3, 3, Color.White)
            pxRect(left, top, cw, ch, 9, 4 + bob, 3, 3, Color.White)
            pxRect(left, top, cw, ch, 5, 5 + bob, 1, 2, Color.Black)
            pxRect(left, top, cw, ch, 10, 5 + bob, 1, 2, Color.Black)
            pxRect(left, top, cw, ch, 4, 9 + bob, 8, 2, Color(0xFFF8F8F8))
        }

        EnemyKind.GHOST -> {
            // Duch procházející cihlovými zdmi
            val cyan = Color(0xFF68D8F8)
            pxRect(left, top, cw, ch, 3, 2 + bob, 10, 11, cyan)
            pxRect(left, top, cw, ch, 2, 4 + bob, 12, 8, cyan)
            pxRect(left, top, cw, ch, 2, 12 + bob, 2, 2, cyan)
            pxRect(left, top, cw, ch, 7, 12 + bob, 2, 2, cyan)
            pxRect(left, top, cw, ch, 12, 12 + bob, 2, 2, cyan)
            pxRect(left, top, cw, ch, 4, 5 + bob, 2, 3, Color(0xFFD80000))
            pxRect(left, top, cw, ch, 10, 5 + bob, 2, 3, Color(0xFFD80000))
        }

        EnemyKind.BEETLE -> {
            // Červeno-černý brouk pro Bombíče
            pxRect(left, top, cw, ch, 3, 3 + bob, 10, 10, Color(0xFFD81818))
            pxRect(left, top, cw, ch, 4, 2 + bob, 8, 3, Color(0xFF101010))
            pxRect(left, top, cw, ch, 7, 5 + bob, 2, 8, Color(0xFF101010))
            pxRect(left, top, cw, ch, 5, 2 + bob, 2, 2, Color.White)
            pxRect(left, top, cw, ch, 9, 2 + bob, 2, 2, Color.White)
        }

        EnemyKind.PONTAN -> {
            // Zuřivá rotující mince při vypršení času nebo odpálení dveří
            val col = if (tick % 2 == 0) Color(0xFFF8D800) else Color(0xFFF83800)
            pxRect(left, top, cw, ch, 2, 2, 12, 12, col)
            pxRect(left, top, cw, ch, 4, 5, 2, 2, Color.Black)
            pxRect(left, top, cw, ch, 10, 5, 2, 2, Color.Black)
            pxRect(left, top, cw, ch, 5, 10, 6, 2, Color.Black)
        }
    }
}
