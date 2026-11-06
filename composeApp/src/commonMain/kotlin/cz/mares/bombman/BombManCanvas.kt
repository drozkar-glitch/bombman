package cz.mares.bombman

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (tick < 0) return@Canvas

                val cw = size.width / engine.gridWidth
                val ch = size.height / engine.gridHeight

                // 1. Podlaha a zdi
                for (gx in 0 until engine.gridWidth) {
                    for (gy in 0 until engine.gridHeight) {
                        val left = gx * cw
                        val top = gy * ch

                        drawFloorTile(engine.edition, gx, gy, left, top, cw, ch)

                        when (engine.getTile(gx, gy)) {
                            TileType.HARD_WALL -> drawHardWall(engine.edition, gx, gy, left, top, cw, ch)
                            TileType.SOFT_WALL -> drawSoftWall(engine.edition, left, top, cw, ch)
                            TileType.EMPTY -> {
                                if (engine.exitRevealed && gx == engine.exitX && gy == engine.exitY) {
                                    val allDead = engine.enemies.none { it.alive }
                                    drawExitPortal(engine.edition, left, top, cw, ch, allDead, tick)
                                }
                                val pu = engine.getVisiblePowerUp(gx, gy)
                                if (pu != PowerUpType.NONE) {
                                    drawPowerUpItem(pu, left, top, cw, ch, tick)
                                }
                            }
                        }
                    }
                }

                // 2. Položené bomby
                for (b in engine.bombs) {
                    drawBomb(b.x * cw, b.y * ch, cw, ch, b.ticksLeft)
                }

                // 3. Plameny výbuchů
                for (f in engine.flames) {
                    drawFlame(f.x * cw, f.y * ch, cw, ch, f.ticksLeft)
                }

                // 4. Nepřátelé a AI Bomberman soupeři
                for (e in engine.enemies) {
                    if (e.alive) {
                        drawEnemy(e, e.x * cw, e.y * ch, cw, ch, tick)
                    }
                }

                // 5. Hráč
                if (!engine.isGameOver) {
                    val blinkHide = engine.playerInvulnerableTicks > 0 && (engine.playerInvulnerableTicks % 4 < 2)
                    if (!blinkHide) {
                        if (engine.edition == GameEdition.BOMBIC) {
                            drawWormPlayer(engine.playerX * cw, engine.playerY * ch, cw, ch, engine.playerFacing)
                        } else {
                            val suitColor = when (engine.edition) {
                                GameEdition.DYNA -> Color(0xFF2563EB)
                                GameEdition.ATOMIC -> Color(0xFF10B981)
                                GameEdition.MRBOOM -> Color(0xFF8B5CF6)
                                GameEdition.BOMBIC -> Color(0xFF2563EB)
                            }
                            drawBomberCharacter(
                                left = engine.playerX * cw,
                                top = engine.playerY * ch,
                                cw = cw,
                                ch = ch,
                                helmetColor = Color.White,
                                suitColor = suitColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawFloorTile(
    edition: GameEdition,
    gx: Int,
    gy: Int,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float
) {
    val isEven = (gx + gy) % 2 == 0
    val color = when (edition) {
        GameEdition.DYNA -> if (isEven) Color(0xFF1E5631) else Color(0xFF194A2A)
        GameEdition.BOMBIC -> if (isEven) Color(0xFF3F6212) else Color(0xFF365314)
        GameEdition.ATOMIC -> if (isEven) Color(0xFF1E293B) else Color(0xFF172033)
        GameEdition.MRBOOM -> if (isEven) Color(0xFF2E1065) else Color(0xFF240B52)
    }
    drawRect(color = color, topLeft = Offset(left, top), size = Size(cw + 0.5f, ch + 0.5f))
}

private fun DrawScope.drawHardWall(
    edition: GameEdition,
    gx: Int,
    gy: Int,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float
) {
    when (edition) {
        GameEdition.BOMBIC -> {
            drawRect(color = Color(0xFF475569), topLeft = Offset(left, top), size = Size(cw, ch))
            drawRoundRect(
                color = Color(0xFF94A3B8),
                topLeft = Offset(left + cw * 0.08f, top + ch * 0.08f),
                size = Size(cw * 0.84f, ch * 0.84f),
                cornerRadius = CornerRadius(cw * 0.28f, ch * 0.28f)
            )
            drawCircle(
                color = Color(0xFFCBD5E1),
                radius = cw * 0.16f,
                center = Offset(left + cw * 0.35f, top + ch * 0.35f)
            )
        }
        else -> {
            val baseColor = if (gx == 0 || gy == 0) Color(0xFF475569) else Color(0xFF64748B)
            drawRect(color = baseColor, topLeft = Offset(left, top), size = Size(cw, ch))
            drawRect(
                color = Color(0xFF94A3B8),
                topLeft = Offset(left + cw * 0.1f, top + ch * 0.1f),
                size = Size(cw * 0.8f, ch * 0.8f)
            )
            drawRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(left, top),
                size = Size(cw, ch),
                style = Stroke(width = 1.5f)
            )
            val r = cw * 0.06f
            drawCircle(Color(0xFF334155), r, Offset(left + cw * 0.2f, top + ch * 0.2f))
            drawCircle(Color(0xFF334155), r, Offset(left + cw * 0.8f, top + ch * 0.2f))
            drawCircle(Color(0xFF334155), r, Offset(left + cw * 0.2f, top + ch * 0.8f))
            drawCircle(Color(0xFF334155), r, Offset(left + cw * 0.8f, top + ch * 0.8f))
        }
    }
}

private fun DrawScope.drawSoftWall(
    edition: GameEdition,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float
) {
    when (edition) {
        GameEdition.BOMBIC -> {
            drawRoundRect(
                color = Color(0xFF15803D),
                topLeft = Offset(left + cw * 0.06f, top + ch * 0.06f),
                size = Size(cw * 0.88f, ch * 0.88f),
                cornerRadius = CornerRadius(cw * 0.25f, ch * 0.25f)
            )
            drawCircle(Color(0xFF22C55E), cw * 0.24f, Offset(left + cw * 0.35f, top + ch * 0.38f))
            drawCircle(Color(0xFF22C55E), cw * 0.22f, Offset(left + cw * 0.65f, top + ch * 0.42f))
            drawCircle(Color(0xFF4ADE80), cw * 0.18f, Offset(left + cw * 0.50f, top + ch * 0.62f))
        }
        GameEdition.ATOMIC -> {
            drawRect(Color(0xFFB45309), Offset(left + 1f, top + 1f), Size(cw - 2f, ch - 2f))
            drawRect(Color(0xFFF59E0B), Offset(left + cw * 0.12f, top + ch * 0.12f), Size(cw * 0.76f, ch * 0.76f))
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(left + cw * 0.15f, top + ch * 0.15f),
                end = Offset(left + cw * 0.85f, top + ch * 0.85f),
                strokeWidth = 3f
            )
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(left + cw * 0.85f, top + ch * 0.15f),
                end = Offset(left + cw * 0.15f, top + ch * 0.85f),
                strokeWidth = 3f
            )
        }
        else -> {
            drawRect(Color(0xFFB91C1C), Offset(left + 1f, top + 1f), Size(cw - 2f, ch - 2f))
            val mortar = Color(0xFFE2E8F0)
            drawLine(mortar, Offset(left, top + ch * 0.5f), Offset(left + cw, top + ch * 0.5f), strokeWidth = 2f)
            drawLine(mortar, Offset(left + cw * 0.5f, top), Offset(left + cw * 0.5f, top + ch * 0.5f), strokeWidth = 2f)
            drawLine(mortar, Offset(left + cw * 0.25f, top + ch * 0.5f), Offset(left + cw * 0.25f, top + ch), strokeWidth = 2f)
            drawLine(mortar, Offset(left + cw * 0.75f, top + ch * 0.5f), Offset(left + cw * 0.75f, top + ch), strokeWidth = 2f)
        }
    }
}

private fun DrawScope.drawExitPortal(
    edition: GameEdition,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    unlocked: Boolean,
    tick: Int
) {
    val glow = if (unlocked && tick % 6 < 3) Color(0xFF22C55E) else Color(0xFFFACC15)
    if (edition == GameEdition.BOMBIC) {
        drawCircle(
            color = glow,
            radius = cw * 0.42f,
            center = Offset(left + cw * 0.5f, top + ch * 0.5f)
        )
        drawCircle(
            color = Color(0xFF1C1917),
            radius = cw * 0.30f,
            center = Offset(left + cw * 0.5f, top + ch * 0.5f)
        )
    } else {
        drawRoundRect(
            color = glow,
            topLeft = Offset(left + cw * 0.14f, top + ch * 0.1f),
            size = Size(cw * 0.72f, ch * 0.82f),
            cornerRadius = CornerRadius(cw * 0.2f, ch * 0.2f)
        )
        drawRoundRect(
            color = if (unlocked) Color(0xFF059669) else Color(0xFF475569),
            topLeft = Offset(left + cw * 0.22f, top + ch * 0.18f),
            size = Size(cw * 0.56f, ch * 0.68f),
            cornerRadius = CornerRadius(cw * 0.12f, ch * 0.12f)
        )
    }
}

private fun DrawScope.drawPowerUpItem(
    pu: PowerUpType,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    tick: Int
) {
    val bgColor = when (pu) {
        PowerUpType.EXTRA_BOMB -> Color(0xFF3B82F6)
        PowerUpType.FIRE_UP -> Color(0xFFEF4444)
        PowerUpType.SPEED_UP -> Color(0xFF10B981)
        PowerUpType.KICK_BOMB -> Color(0xFFF59E0B)
        PowerUpType.EXTRA_LIFE -> Color(0xFFEC4899)
        PowerUpType.NONE -> Color.Transparent
    }
    val pulse = if (tick % 8 < 4) 0.12f else 0.16f
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + cw * pulse, top + ch * pulse),
        size = Size(cw * (1f - 2 * pulse), ch * (1f - 2 * pulse)),
        cornerRadius = CornerRadius(cw * 0.2f, ch * 0.2f)
    )
    drawRoundRect(
        color = bgColor,
        topLeft = Offset(left + cw * (pulse + 0.05f), top + ch * (pulse + 0.05f)),
        size = Size(cw * (0.9f - 2 * pulse), ch * (0.9f - 2 * pulse)),
        cornerRadius = CornerRadius(cw * 0.16f, ch * 0.16f)
    )
    val cx = left + cw * 0.5f
    val cy = top + ch * 0.5f
    when (pu) {
        PowerUpType.EXTRA_BOMB -> drawCircle(Color.Black, cw * 0.18f, Offset(cx, cy))
        PowerUpType.FIRE_UP -> drawCircle(Color(0xFFFFEB3B), cw * 0.18f, Offset(cx, cy))
        PowerUpType.SPEED_UP -> drawRect(Color.White, Offset(cx - cw * 0.16f, cy - ch * 0.08f), Size(cw * 0.32f, ch * 0.16f))
        PowerUpType.KICK_BOMB -> drawCircle(Color.White, cw * 0.15f, Offset(cx, cy))
        PowerUpType.EXTRA_LIFE -> drawCircle(Color.White, cw * 0.17f, Offset(cx, cy))
        PowerUpType.NONE -> {}
    }
}

private fun DrawScope.drawBomb(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    ticksLeft: Int
) {
    val cx = left + cw * 0.5f
    val cy = top + ch * 0.54f
    val pulse = if (ticksLeft % 6 < 3) 0.38f else 0.34f

    drawCircle(
        color = if (ticksLeft <= 12 && ticksLeft % 2 == 0) Color(0xFFDC2626) else Color(0xFF0F172A),
        radius = cw * pulse,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF64748B),
        radius = cw * 0.10f,
        center = Offset(cx - cw * 0.12f, cy - ch * 0.12f)
    )
    drawLine(
        color = Color(0xFFFBBF24),
        start = Offset(cx, cy - ch * 0.32f),
        end = Offset(cx + cw * 0.18f, top + ch * 0.12f),
        strokeWidth = 3f
    )
    val sparkColor = if (ticksLeft % 2 == 0) Color(0xFFFF5722) else Color(0xFFFFEB3B)
    drawCircle(
        color = sparkColor,
        radius = cw * 0.11f,
        center = Offset(cx + cw * 0.18f, top + ch * 0.12f)
    )
}

private fun DrawScope.drawFlame(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    ticksLeft: Int
) {
    val outer = if (ticksLeft % 2 == 0) Color(0xFFFF5722) else Color(0xFFEF4444)
    val inner = Color(0xFFFFEB3B)

    drawRoundRect(
        color = outer,
        topLeft = Offset(left + cw * 0.06f, top + ch * 0.06f),
        size = Size(cw * 0.88f, ch * 0.88f),
        cornerRadius = CornerRadius(cw * 0.25f, ch * 0.25f)
    )
    drawRoundRect(
        color = inner,
        topLeft = Offset(left + cw * 0.22f, top + ch * 0.22f),
        size = Size(cw * 0.56f, ch * 0.56f),
        cornerRadius = CornerRadius(cw * 0.18f, ch * 0.18f)
    )
}

private fun DrawScope.drawBomberCharacter(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    helmetColor: Color,
    suitColor: Color
) {
    val cx = left + cw * 0.5f
    drawRoundRect(
        color = suitColor,
        topLeft = Offset(left + cw * 0.26f, top + ch * 0.52f),
        size = Size(cw * 0.48f, ch * 0.38f),
        cornerRadius = CornerRadius(cw * 0.15f, ch * 0.15f)
    )
    drawCircle(
        color = helmetColor,
        radius = cw * 0.32f,
        center = Offset(cx, top + ch * 0.38f)
    )
    drawCircle(
        color = Color(0xFFEC4899),
        radius = cw * 0.09f,
        center = Offset(cx, top + ch * 0.09f)
    )
    drawRoundRect(
        color = Color(0xFFFDE68A),
        topLeft = Offset(left + cw * 0.28f, top + ch * 0.28f),
        size = Size(cw * 0.44f, ch * 0.20f),
        cornerRadius = CornerRadius(cw * 0.08f, ch * 0.08f)
    )
    drawCircle(Color.Black, cw * 0.045f, Offset(cx - cw * 0.10f, top + ch * 0.38f))
    drawCircle(Color.Black, cw * 0.045f, Offset(cx + cw * 0.10f, top + ch * 0.38f))
}

private fun DrawScope.drawWormPlayer(
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    facing: Direction
) {
    val cx = left + cw * 0.5f
    val cy = top + ch * 0.55f
    drawCircle(Color(0xFFF472B6), cw * 0.22f, Offset(cx - facing.dx * cw * 0.16f, cy + ch * 0.14f))
    drawCircle(Color(0xFFFBBF24), cw * 0.28f, Offset(cx, cy))
    drawCircle(Color(0xFFFDE047), cw * 0.30f, Offset(cx, top + ch * 0.36f))
    drawCircle(Color.White, cw * 0.11f, Offset(cx - cw * 0.11f, top + ch * 0.32f))
    drawCircle(Color.White, cw * 0.11f, Offset(cx + cw * 0.11f, top + ch * 0.32f))
    drawCircle(Color.Black, cw * 0.055f, Offset(cx - cw * 0.10f, top + ch * 0.32f))
    drawCircle(Color.Black, cw * 0.055f, Offset(cx + cw * 0.10f, top + ch * 0.32f))
}

private fun DrawScope.drawEnemy(
    enemy: EnemyActor,
    left: Float,
    top: Float,
    cw: Float,
    ch: Float,
    tick: Int
) {
    when (enemy.kind) {
        EnemyKind.RIVAL_RED -> drawBomberCharacter(left, top, cw, ch, Color(0xFFEF4444), Color(0xFF7F1D1D))
        EnemyKind.RIVAL_BLUE -> drawBomberCharacter(left, top, cw, ch, Color(0xFF38BDF8), Color(0xFF0C4A6E))
        EnemyKind.RIVAL_GOLD -> drawBomberCharacter(left, top, cw, ch, Color(0xFFFACC15), Color(0xFF713F12))
        EnemyKind.BALLOON -> {
            val bounce = if (tick % 6 < 3) -ch * 0.03f else ch * 0.03f
            val cx = left + cw * 0.5f
            val cy = top + ch * 0.48f + bounce
            drawCircle(Color(0xFFF97316), cw * 0.36f, Offset(cx, cy))
            drawCircle(Color.White, cw * 0.09f, Offset(cx - cw * 0.12f, cy - ch * 0.06f))
            drawCircle(Color.White, cw * 0.09f, Offset(cx + cw * 0.12f, cy - ch * 0.06f))
            drawCircle(Color.Black, cw * 0.045f, Offset(cx - cw * 0.12f, cy - ch * 0.06f))
            drawCircle(Color.Black, cw * 0.045f, Offset(cx + cw * 0.12f, cy - ch * 0.06f))
        }
        EnemyKind.GHOST -> {
            val cx = left + cw * 0.5f
            val cy = top + ch * 0.46f
            val path = Path().apply {
                moveTo(left + cw * 0.16f, top + ch * 0.85f)
                lineTo(left + cw * 0.16f, top + ch * 0.40f)
                lineTo(left + cw * 0.50f, top + ch * 0.14f)
                lineTo(left + cw * 0.84f, top + ch * 0.40f)
                lineTo(left + cw * 0.84f, top + ch * 0.85f)
                close()
            }
            drawPath(path, Color(0xFF38BDF8))
            drawCircle(Color(0xFF38BDF8), cw * 0.34f, Offset(cx, cy))
            drawCircle(Color.Yellow, cw * 0.08f, Offset(cx - cw * 0.12f, cy - ch * 0.04f))
            drawCircle(Color.Yellow, cw * 0.08f, Offset(cx + cw * 0.12f, cy - ch * 0.04f))
        }
        EnemyKind.BEETLE -> {
            val cx = left + cw * 0.5f
            val cy = top + ch * 0.52f
            drawCircle(Color(0xFFDC2626), cw * 0.35f, Offset(cx, cy))
            drawCircle(Color.Black, cw * 0.18f, Offset(cx, top + ch * 0.25f))
            drawCircle(Color.Black, cw * 0.06f, Offset(cx - cw * 0.14f, cy))
            drawCircle(Color.Black, cw * 0.06f, Offset(cx + cw * 0.14f, cy))
            drawCircle(Color.White, cw * 0.05f, Offset(cx - cw * 0.08f, top + ch * 0.22f))
            drawCircle(Color.White, cw * 0.05f, Offset(cx + cw * 0.08f, top + ch * 0.22f))
        }
    }
}
