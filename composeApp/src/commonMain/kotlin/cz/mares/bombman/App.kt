package cz.mares.bombman

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class AppScreen {
    MENU,
    PLAYING,
    LEADERBOARD
}

@Composable
fun App() {
    MaterialTheme {
        val engine = remember { GameEngine() }
        var currentScreen by remember { mutableStateOf(AppScreen.MENU) }
        var selectedEdition by remember { mutableStateOf(GameEdition.DYNA) }
        var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
        var soundOn by remember { mutableStateOf(LeaderboardRepository.soundEnabled) }

        LaunchedEffect(currentScreen, engine.isPaused, engine.isGameOver) {
            while (currentScreen == AppScreen.PLAYING && !engine.isPaused && !engine.isGameOver) {
                delay(45L)
                engine.tick()
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080808))
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = Color(0xFF080808)
        ) {
            when (currentScreen) {
                AppScreen.MENU -> MainMenuScreen(
                    selectedEdition = selectedEdition,
                    selectedDifficulty = selectedDifficulty,
                    soundOn = soundOn,
                    onSelectEdition = { selectedEdition = it },
                    onSelectDifficulty = { selectedDifficulty = it },
                    onToggleSound = {
                        soundOn = !soundOn
                        LeaderboardRepository.soundEnabled = soundOn
                    },
                    onStartGame = {
                        engine.startNewGame(selectedEdition, selectedDifficulty)
                        currentScreen = AppScreen.PLAYING
                    },
                    onOpenLeaderboard = {
                        currentScreen = AppScreen.LEADERBOARD
                    }
                )

                AppScreen.PLAYING -> PlayingScreen(
                    engine = engine,
                    soundOn = soundOn,
                    onToggleSound = {
                        soundOn = !soundOn
                        LeaderboardRepository.soundEnabled = soundOn
                    },
                    onOpenLeaderboard = {
                        engine.heldDirection = Direction.NONE
                        currentScreen = AppScreen.LEADERBOARD
                    },
                    onBackToMenu = {
                        engine.heldDirection = Direction.NONE
                        currentScreen = AppScreen.MENU
                    }
                )

                AppScreen.LEADERBOARD -> LeaderboardScreen(
                    initialEdition = selectedEdition,
                    currentScore = engine.score,
                    onBack = {
                        currentScreen = AppScreen.MENU
                    }
                )
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    selectedEdition: GameEdition,
    selectedDifficulty: Difficulty,
    soundOn: Boolean,
    onSelectEdition: (GameEdition) -> Unit,
    onSelectDifficulty: (Difficulty) -> Unit,
    onToggleSound: () -> Unit,
    onStartGame: () -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Retro horní hlavička ve stylu DOS Dyna Blaster
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFA0A0A0))
                    .border(2.dp, Color(0xFFE8E8E8))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF404040))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BOMBMAN • RETRO 1992",
                        color = Color(0xFFF8D800),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF000000))
                        .border(1.5.dp, Color(0xFFF89800))
                        .clickable { onToggleSound() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (soundOn) "🔊 ZVUK ZAP" else "🔇 ZVUK VYP",
                        color = if (soundOn) Color(0xFF48F848) else Color(0xFFA0A0A0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Střední posuvná část
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "VYBER HERNÍ EDICI (DRUHÝM KLIKNUTÍM SPUSTÍŠ):",
                    color = Color(0xFFF89800),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (isLandscape) {
                    val entries = GameEdition.entries
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditionCard(
                            ed = entries[0],
                            isSelected = entries[0] == selectedEdition,
                            onSelect = {
                                if (selectedEdition == entries[0]) onStartGame() else onSelectEdition(entries[0])
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditionCard(
                            ed = entries[1],
                            isSelected = entries[1] == selectedEdition,
                            onSelect = {
                                if (selectedEdition == entries[1]) onStartGame() else onSelectEdition(entries[1])
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditionCard(
                            ed = entries[2],
                            isSelected = entries[2] == selectedEdition,
                            onSelect = {
                                if (selectedEdition == entries[2]) onStartGame() else onSelectEdition(entries[2])
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditionCard(
                            ed = entries[3],
                            isSelected = entries[3] == selectedEdition,
                            onSelect = {
                                if (selectedEdition == entries[3]) onStartGame() else onSelectEdition(entries[3])
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    for (ed in GameEdition.entries) {
                        EditionCard(
                            ed = ed,
                            isSelected = ed == selectedEdition,
                            onSelect = {
                                if (selectedEdition == ed) onStartGame() else onSelectEdition(ed)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RYCHLOST A OBTÍŽNOST:",
                    color = Color(0xFFF89800),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (diff in Difficulty.entries) {
                        val active = (diff == selectedDifficulty)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) Color(0xFFD82800) else Color(0xFF181818))
                                .border(
                                    width = 2.dp,
                                    color = if (active) Color(0xFFF8D800) else Color(0xFF585858)
                                )
                                .clickable { onSelectDifficulty(diff) }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${diff.label} (${diff.scoreMult}×)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Spodní akční lišta
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartGame,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(42.dp)
                                .border(2.dp, Color(0xFFF8D800)),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF188818))
                        ) {
                            Text(
                                text = "▶ START: ${selectedEdition.title}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onOpenLeaderboard,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(2.dp, Color(0xFF888888)),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF202020))
                        ) {
                            Text(
                                text = "🏆 REKORDY (TOP 50)",
                                color = Color(0xFFF8D800),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onStartGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .border(2.dp, Color(0xFFF8D800)),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF188818))
                    ) {
                        Text(
                            text = "▶ START: ${selectedEdition.title}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onOpenLeaderboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(2.dp, Color(0xFF888888)),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF202020))
                    ) {
                        Text(
                            text = "🏆 ONLINE REKORDY (TOP 50)",
                            color = Color(0xFFF8D800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "© 2026 Created by Pavel Mareš",
                    color = Color(0xFF787878),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EditionCard(
    ed: GameEdition,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderCol = if (isSelected) Color(0xFFF8D800) else Color(0xFF585858)
    val bgCol = if (isSelected) Color(0xFF182438) else Color(0xFF101010)
    val best = LeaderboardRepository.getLocalBestScore(ed.firebaseKey)

    Column(
        modifier = modifier
            .background(bgCol)
            .border(if (isSelected) 2.dp else 1.dp, borderCol)
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${ed.title} (${ed.year})",
                color = if (isSelected) Color(0xFFF8D800) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(Color.Black)
                    .border(1.dp, Color(0xFFF89800))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "TOP: $best",
                    color = Color(0xFFF8D800),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = ed.description,
            color = Color(0xFFD0D0D0),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun PlayingScreen(
    engine: GameEngine,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val mins = engine.timeLeftSeconds / 60
    val secs = engine.timeLeftSeconds % 60
    val timeStr = "$mins:${if (secs < 10) "0$secs" else "$secs"}"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // ROZLOŽENÍ NA LEŽATO (Levý sloupec: Retro HUD + D-Pad, Střed: Celá aréna 15x11, Pravý sloupec: Menu + A/B tlačítka)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Levý panel: Klasický Dyna Blaster HUD + plynulý D-Pad
                Column(
                    modifier = Modifier
                        .width(146.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFA0A0A0))
                            .border(2.dp, Color(0xFFE8E8E8))
                            .padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RetroDigitalBox(label = "TIME", value = timeStr, valueColor = if (engine.timeLeftSeconds <= 30) Color(0xFFF83800) else Color.White)
                        RetroDigitalBox(label = "SCORE", value = "${engine.score}", valueColor = Color(0xFFF8D800))
                        RetroDigitalBox(label = "LEFT", value = "${engine.lives} • ST.${engine.level}", valueColor = Color(0xFF48F848))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "💣${engine.maxBombs} 🔥${engine.fireRange}",
                                color = Color(0xFF68D8F8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = buildString {
                                    if (engine.hasDetonator) append("B ")
                                    if (engine.hasKick) append("👟")
                                    if (engine.hasWallPass) append("🧱")
                                }.ifEmpty { engine.edition.shortTab },
                                color = Color(0xFFF89800),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Plynulý dotykový D-Pad (podporuje držení i sklouznutí palce)
                    RetroTouchDPad(
                        activeDir = engine.heldDirection,
                        padSize = 136.dp,
                        onDirectionChange = { dir ->
                            engine.heldDirection = dir
                            if (dir != Direction.NONE) {
                                engine.movePlayer(dir)
                            }
                        }
                    )
                }

                // 2. Střední panel: Kompletní herní aréna
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = engine.statusMessage,
                        color = Color(0xFFF8D800),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BombManBoard(
                            engine = engine,
                            modifier = Modifier.fillMaxSize()
                        )
                        GameOverlays(
                            engine = engine,
                            onOpenLeaderboard = onOpenLeaderboard
                        )
                    }
                }

                // 3. Pravý panel: Ovládání nahoře + Tlačítka A (BOMBA) a B (ODPAL) + Copyright
                Column(
                    modifier = Modifier
                        .width(124.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SmallHudButton(text = if (soundOn) "🔊" else "🔇", onClick = onToggleSound)
                        SmallHudButton(
                            text = if (engine.isPaused) "▶" else "⏸",
                            onClick = { engine.isPaused = !engine.isPaused }
                        )
                        SmallHudButton(text = "EXIT", onClick = onBackToMenu)
                    }

                    // Akční tlačítka B (Odpal) a A (Bomba)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RetroActionButton(
                            code = "B",
                            label = "ODPAL",
                            enabled = engine.hasDetonator,
                            bgColor = if (engine.hasDetonator) Color(0xFF1858C8) else Color(0xFF282828),
                            borderColor = if (engine.hasDetonator) Color(0xFF68D8F8) else Color(0xFF585858),
                            btnSize = 54.dp,
                            onClick = { engine.detonateOldestBomb() }
                        )
                        RetroActionButton(
                            code = "A",
                            label = "BOMBA",
                            enabled = true,
                            bgColor = Color(0xFFD82800),
                            borderColor = Color(0xFFF8B800),
                            btnSize = 86.dp,
                            onClick = { engine.placeBomb() }
                        )
                    }

                    Text(
                        text = "© 2026 Created by\nPavel Mareš",
                        color = Color(0xFF787878),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // ROZLOŽENÍ NA VÝŠKU (Klasická horní lišta z Dyna Blasteru + Aréna + D-Pad a A/B tlačítka dole)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Horní retro panel ve stylu DOS Dyna Blaster
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFA0A0A0))
                        .border(2.dp, Color(0xFFE8E8E8))
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RetroTopCell(label = "TIME", value = timeStr, valueColor = if (engine.timeLeftSeconds <= 30) Color(0xFFF83800) else Color.White)
                        RetroTopCell(label = "SCORE", value = "${engine.score}", valueColor = Color(0xFFF8D800))
                        RetroTopCell(label = "LEFT", value = "${engine.lives}", valueColor = Color(0xFF48F848))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallHudButton(text = if (soundOn) "🔊" else "🔇", onClick = onToggleSound)
                            SmallHudButton(
                                text = if (engine.isPaused) "▶" else "⏸",
                                onClick = { engine.isPaused = !engine.isPaused }
                            )
                            SmallHudButton(text = "EXIT", onClick = onBackToMenu)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STAGE ${engine.level} • ${engine.edition.shortTab}",
                            color = Color(0xFFF89800),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "💣${engine.maxBombs} 🔥${engine.fireRange}" +
                                (if (engine.hasDetonator) " [B:ODPAL]" else "") +
                                (if (engine.hasKick) " [KOP]" else ""),
                            color = Color(0xFF68D8F8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = engine.statusMessage,
                    color = Color(0xFFF8D800),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Herní aréna uprostřed
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BombManBoard(
                        engine = engine,
                        modifier = Modifier.fillMaxSize()
                    )
                    GameOverlays(
                        engine = engine,
                        onOpenLeaderboard = onOpenLeaderboard
                    )
                }

                // Spodní ovládací konzole: Vlevo plynulý D-Pad, vpravo tlačítka B (ODPAL) a A (BOMBA)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RetroTouchDPad(
                            activeDir = engine.heldDirection,
                            padSize = 144.dp,
                            onDirectionChange = { dir ->
                                engine.heldDirection = dir
                                if (dir != Direction.NONE) {
                                    engine.movePlayer(dir)
                                }
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RetroActionButton(
                                code = "B",
                                label = "ODPAL",
                                enabled = engine.hasDetonator,
                                bgColor = if (engine.hasDetonator) Color(0xFF1858C8) else Color(0xFF282828),
                                borderColor = if (engine.hasDetonator) Color(0xFF68D8F8) else Color(0xFF585858),
                                btnSize = 62.dp,
                                onClick = { engine.detonateOldestBomb() }
                            )
                            RetroActionButton(
                                code = "A",
                                label = "BOMBA",
                                enabled = true,
                                bgColor = Color(0xFFD82800),
                                borderColor = Color(0xFFF8B800),
                                btnSize = 94.dp,
                                onClick = { engine.placeBomb() }
                            )
                        }
                    }

                    Text(
                        text = "© 2026 Created by Pavel Mareš",
                        color = Color(0xFF787878),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun RetroTopCell(label: String, value: String, valueColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.Black)
            .border(1.dp, Color(0xFFF89800))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$label ",
            color = Color(0xFFF89800),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun RetroDigitalBox(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(1.dp, Color(0xFFF89800))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFF89800),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Dotykový směrový kříž (D-Pad), který podporuje plynulé držení prstu
 * i přejíždění palcem mezi směry bez zvednutí prstu z obrazovky.
 */
@Composable
private fun RetroTouchDPad(
    activeDir: Direction,
    padSize: Dp,
    onDirectionChange: (Direction) -> Unit
) {
    Box(
        modifier = Modifier
            .size(padSize)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent()
                    val firstChange = down.changes.firstOrNull()
                    if (firstChange != null && firstChange.pressed) {
                        val dir = computeDPadDirection(firstChange.position.x, firstChange.position.y, size.width.toFloat(), size.height.toFloat())
                        onDirectionChange(dir)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointer = event.changes.firstOrNull { it.pressed }
                        if (activePointer != null) {
                            activePointer.consume()
                            val dir = computeDPadDirection(
                                activePointer.position.x,
                                activePointer.position.y,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                            onDirectionChange(dir)
                        } else {
                            onDirectionChange(Direction.NONE)
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val armThickness = padSize * 0.34f

        // Svislé rameno kříže
        Column(
            modifier = Modifier
                .width(armThickness)
                .fillMaxHeight()
                .background(Color(0xFF202020))
                .border(2.dp, Color(0xFF787878)),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (activeDir == Direction.UP) Color(0xFFF89800) else Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Text("▲", color = if (activeDir == Direction.UP) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(armThickness))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (activeDir == Direction.DOWN) Color(0xFFF89800) else Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Text("▼", color = if (activeDir == Direction.DOWN) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Vodorovné rameno kříže
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(armThickness)
                .background(Color(0xFF202020))
                .border(2.dp, Color(0xFF787878)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(if (activeDir == Direction.LEFT) Color(0xFFF89800) else Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Text("◀", color = if (activeDir == Direction.LEFT) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(armThickness)
                    .background(Color(0xFF181818)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(armThickness * 0.45f)
                        .clip(CircleShape)
                        .background(Color(0xFF404040))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(if (activeDir == Direction.RIGHT) Color(0xFFF89800) else Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = if (activeDir == Direction.RIGHT) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun computeDPadDirection(x: Float, y: Float, w: Float, h: Float): Direction {
    val cx = w * 0.5f
    val cy = h * 0.5f
    val dx = x - cx
    val dy = y - cy
    val deadZone = w * 0.07f
    if (abs(dx) < deadZone && abs(dy) < deadZone) return Direction.NONE
    return if (abs(dx) > abs(dy)) {
        if (dx > 0f) Direction.RIGHT else Direction.LEFT
    } else {
        if (dy > 0f) Direction.DOWN else Direction.UP
    }
}

@Composable
private fun RetroActionButton(
    code: String,
    label: String,
    enabled: Boolean,
    bgColor: Color,
    borderColor: Color,
    btnSize: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(btnSize)
            .clip(CircleShape)
            .background(bgColor)
            .border(3.dp, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = code,
                color = if (enabled) Color(0xFFF8F800) else Color(0xFF888888),
                fontSize = (btnSize.value * 0.24f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = if (enabled) Color.White else Color(0xFF888888),
                fontSize = (btnSize.value * 0.13f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun GameOverlays(
    engine: GameEngine,
    onOpenLeaderboard: () -> Unit
) {
    // Klasická černá obrazovka "STAGE X" před začátkem kola jako v Dyna Blasteru
    if (engine.stageIntroTicks > 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "STAGE  ${engine.level}",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = engine.edition.title,
                    color = Color(0xFFF89800),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        return
    }

    if (engine.isVictoryBanner) {
        Box(
            modifier = Modifier
                .background(Color.Black)
                .border(2.dp, Color(0xFF48F848))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "STAGE ${engine.level} CLEAR!",
                    color = Color(0xFF48F848),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SCORE: ${engine.score}",
                    color = Color(0xFFF8D800),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    if (engine.isGameOver) {
        Box(
            modifier = Modifier
                .background(Color.Black)
                .border(2.dp, Color(0xFFF83800))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GAME OVER",
                    color = Color(0xFFF83800),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SCORE: ${engine.score}",
                    color = Color(0xFFF8D800),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { engine.startNewGame(engine.edition, engine.difficulty) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF188818))
                    ) {
                        Text("CONTINUE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = onOpenLeaderboard,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1858C8))
                    ) {
                        Text("TOP 50", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallHudButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Black)
            .border(1.dp, Color(0xFFF89800))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun LeaderboardScreen(
    initialEdition: GameEdition,
    currentScore: Int,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf(initialEdition) }
    var savedNick by remember { mutableStateOf(LeaderboardRepository.getSavedNick()) }
    var nickInput by remember { mutableStateOf("") }
    var scores by remember { mutableStateOf<List<ScoreEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    fun loadLeaderboard(edition: GameEdition) {
        isLoading = true
        infoMessage = null
        LeaderboardRepository.fetchTopScores(
            gameId = edition.firebaseKey,
            includeZeroScores = false
        ) { ok, list, err ->
            isLoading = false
            if (ok) {
                scores = list
            } else {
                infoMessage = err ?: "Nepodařilo se načíst žebříček."
            }
        }
    }

    LaunchedEffect(activeTab) {
        loadLeaderboard(activeTab)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ONLINE REKORDY (TOP 50)",
                    color = Color(0xFFF8D800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1858C8))
                        .border(1.dp, Color(0xFF68D8F8))
                        .clickable { loadLeaderboard(activeTab) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "OBNOVIT",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (ed in GameEdition.entries) {
                    val selected = (ed == activeTab)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) Color(0xFFF89800) else Color(0xFF202020))
                            .border(1.dp, if (selected) Color(0xFFF8D800) else Color(0xFF585858))
                            .clickable { activeTab = ed }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ed.shortTab,
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val localBest = LeaderboardRepository.getLocalBestScore(activeTab.firebaseKey)
            val bestToSubmit = if (currentScore > localBest) currentScore else localBest

            if (savedNick.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181818))
                        .border(1.dp, Color(0xFF585858))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Zadej přezdívku pro zápis do TOP 50 (po uložení se uzamkne):",
                        color = Color(0xFFD0D0D0),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black)
                                .border(1.dp, Color(0xFFF89800))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (nickInput.isEmpty()) {
                                Text("Tvoje přezdívka...", color = Color(0xFF787878), fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = nickInput,
                                onValueChange = { if (it.length <= 14) nickInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(Color(0xFFF8D800)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF188818))
                                .border(1.dp, Color(0xFF48F848))
                                .clickable {
                                    LeaderboardRepository.submitScore(
                                        gameId = activeTab.firebaseKey,
                                        rawNick = nickInput,
                                        score = bestToSubmit
                                    ) { ok, msg ->
                                        infoMessage = msg
                                        if (ok) {
                                            savedNick = LeaderboardRepository.getSavedNick()
                                            loadLeaderboard(activeTab)
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "ULOŽIT",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181818))
                        .border(1.dp, Color(0xFFF89800))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HRÁČ: $savedNick",
                        color = Color(0xFF48F848),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "OSOBNÍ: $localBest",
                        color = Color(0xFFF8D800),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (infoMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = infoMessage!!,
                    color = Color(0xFF68D8F8),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF585858))
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Načítám online rekordy...", color = Color(0xFFA0A0A0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                } else if (scores.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Zatím žádné rekordy v kategorii ${activeTab.title}. Buď první!",
                            color = Color(0xFFA0A0A0),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                        itemsIndexed(scores) { idx, item ->
                            val isMe = savedNick.isNotBlank() && item.nick.equals(savedNick, ignoreCase = true)
                            val rowBg = if (isMe) Color(0xFF183868) else if (idx % 2 == 0) Color(0xFF141414) else Color.Transparent
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${item.nick}",
                                    color = if (isMe) Color(0xFFF8D800) else Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (idx < 3 || isMe) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.score} b.",
                                    color = Color(0xFF68D8F8),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(1.dp, Color(0xFF888888)),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF282828))
            ) {
                Text(
                    text = "ZPĚT DO MENU",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "© 2026 Created by Pavel Mareš",
                color = Color(0xFF787878),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
