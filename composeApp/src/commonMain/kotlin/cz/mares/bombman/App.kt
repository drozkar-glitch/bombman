package cz.mares.bombman

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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
                delay(55L)
                engine.tick()
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1120))
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = Color(0xFF0B1120)
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
                        currentScreen = AppScreen.LEADERBOARD
                    },
                    onBackToMenu = {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Pevná horní hlavička (nikdy se nezalomí na 2 řádky)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💣 BOMBMAN 4v1",
                color = Color(0xFFFBBF24),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .clickable { onToggleSound() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (soundOn) "🔊 ZVUK ZAP" else "🔇 ZVUK VYP",
                    color = if (soundOn) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Posuvná střední část s výběrem edice a obtížnosti
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "VYBER HERNÍ EDICI (KLIKNI PRO VÝBĚR):",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            for (ed in GameEdition.entries) {
                val isSelected = (ed == selectedEdition)
                val borderCol = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155)
                val bgCol = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
                val best = LeaderboardRepository.getLocalBestScore(ed.firebaseKey)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgCol)
                        .border(if (isSelected) 2.dp else 1.dp, borderCol, RoundedCornerShape(10.dp))
                        .clickable {
                            if (isSelected) {
                                onStartGame()
                            } else {
                                onSelectEdition(ed)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${ed.title} (${ed.year})",
                            color = if (isSelected) Color(0xFFFBBF24) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REKORD: $best",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = ed.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "OBTÍŽNOST A NÁSOBIČ BODŮ:",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF2563EB) else Color(0xFF1E293B))
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) Color(0xFF60A5FA) else Color(0xFF334155),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectDifficulty(diff) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${diff.label} (${diff.scoreMult}×)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Pevně ukotvený spodní panel s tlačítky (vždy viditelný na každém displeji)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF16A34A))
            ) {
                Text(
                    text = "▶ HRÁT: ${selectedEdition.title}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
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
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1E293B))
            ) {
                Text(
                    text = "🏆 ONLINE REKORDY (TOP 50)",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "© 2026 Created by Pavel Mareš",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Horní stavová lišta
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${engine.edition.shortTab} • KOLO ${engine.level}",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallHudButton(text = if (soundOn) "🔊" else "🔇", onClick = onToggleSound)
                    SmallHudButton(
                        text = if (engine.isPaused) "▶" else "⏸",
                        onClick = { engine.isPaused = !engine.isPaused }
                    )
                    SmallHudButton(text = "MENU", onClick = onBackToMenu)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BODY: ${engine.score}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "❤️ ${engine.lives}",
                    color = Color(0xFFF87171),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "⏱ $timeStr",
                    color = if (engine.timeLeftSeconds <= 30) Color(0xFFEF4444) else Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "💣${engine.maxBombs} 🔥${engine.fireRange}${if (engine.hasKick) " 👟" else ""}",
                    color = Color(0xFFA7F3D0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = engine.statusMessage,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Střední herní plocha (automaticky se přizpůsobí volnému místu)
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

            if (engine.isVictoryBanner) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xEE0F172A))
                        .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(12.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎉 KOLO ${engine.level} DOKONČENO!",
                            color = Color(0xFF4ADE80),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Připrav se na další arénu...",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (engine.isGameOver) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF00F172A))
                        .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "💥 KONEC HRY",
                            color = Color(0xFFF87171),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nahrané skóre: ${engine.score}",
                            color = Color(0xFFFBBF24),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { engine.startNewGame(engine.edition, engine.difficulty) },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF16A34A))
                            ) {
                                Text("HRÁT ZNOVU", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onOpenLeaderboard,
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2563EB))
                            ) {
                                Text("REKORDY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Spodní ovládací panel: D-Pad vlevo + velké tlačítko BOMBA vpravo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // D-Pad
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DPadButton(label = "▲", onClick = { engine.movePlayer(Direction.UP) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DPadButton(label = "◀", onClick = { engine.movePlayer(Direction.LEFT) })
                        Spacer(modifier = Modifier.width(34.dp))
                        DPadButton(label = "▶", onClick = { engine.movePlayer(Direction.RIGHT) })
                    }
                    DPadButton(label = "▼", onClick = { engine.movePlayer(Direction.DOWN) })
                }

                // Tlačítko pro položení bomby
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                        .border(4.dp, Color(0xFFFCA5A5), CircleShape)
                        .clickable { engine.placeBomb() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "💣", fontSize = 24.sp)
                        Text(
                            text = "BOMBA",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Text(
                text = "© 2026 Created by Pavel Mareš",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SmallHudButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DPadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .border(1.5.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
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
            // Horní lišta okna podle pravidel skilly
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ONLINE REKORDY (TOP 50)",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2563EB))
                        .clickable { loadLeaderboard(activeTab) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "OBNOVIT",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Přepínací záložky pro 4 verze hry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (ed in GameEdition.entries) {
                    val selected = (ed == activeTab)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFFF59E0B) else Color(0xFF1E293B))
                            .clickable { activeTab = ed }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ed.shortTab,
                            color = if (selected) Color(0xFF0F172A) else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val localBest = LeaderboardRepository.getLocalBestScore(activeTab.firebaseKey)
            val bestToSubmit = if (currentScore > localBest) currentScore else localBest

            // Zadávací pole se zobrazuje POUZE pokud hráč ještě nemá uložený nick
            if (savedNick.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Zadej přezdívku pro zápis do TOP 50 (po uložení se uzamkne):",
                        color = Color(0xFFCBD5E1),
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (nickInput.isEmpty()) {
                                Text("Tvoje přezdívka...", color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                            BasicTextField(
                                value = nickInput,
                                onValueChange = { if (it.length <= 14) nickInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = SolidColor(Color(0xFFFBBF24)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF16A34A))
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
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                // Uzamčený informační proužek hráče (11.sp podle skilly)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HRÁČ: $savedNick",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "OSOBNÍ: $localBest",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (infoMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = infoMessage!!,
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Seznam TOP 50 hráčů
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Načítám online rekordy...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                } else if (scores.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Zatím žádné rekordy v kategorii ${activeTab.title}. Buď první!",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                        itemsIndexed(scores) { idx, item ->
                            val isMe = savedNick.isNotBlank() && item.nick.equals(savedNick, ignoreCase = true)
                            val rowBg = if (isMe) Color(0xFF1E3A8A) else if (idx % 2 == 0) Color(0xFF1E293B) else Color.Transparent
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(rowBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${item.nick}",
                                    color = if (isMe) Color(0xFFFBBF24) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (idx < 3 || isMe) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.score} b.",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
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

        Spacer(modifier = Modifier.height(8.dp))

        // Spodní tlačítko ZPĚT DO MENU a copyright
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF334155))
            ) {
                Text(
                    text = "ZPĚT DO MENU",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "© 2026 Created by Pavel Mareš",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}
