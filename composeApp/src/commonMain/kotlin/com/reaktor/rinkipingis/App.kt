package com.reaktor.rinkipingis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import rinkipingis.composeapp.generated.resources.Res
import rinkipingis.composeapp.generated.resources.paddle
import kotlin.enums.enumEntries
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class Player {
    Left, Right;

    companion object {
        fun random(): Player {
            val entries = enumEntries<Player>()
            return entries[Random.nextInt(0, entries.size)]
        }
    }
}

inline fun <reified T : Enum<T>> T.next(): T {
    val entries = enumEntries<T>()
    val nextOrdinal = (ordinal + 1) % entries.size
    return entries[nextOrdinal]
}

class Match private constructor(
    private val games: List<Game>
) {
    val currentGame = games.last()
    val finishedGames = games.filterIsInstance<Game.FinishedGame>()


    val reverseOrder = games.size % 2 == 0

    fun point(player: Player): Match {
        return when (val last = games.last()) {
            is Game.FinishedGame -> throw (Exception("Can not add a point to a finished game"))
            is Game.OngoingGame -> {
                val lastGameWithNewPoint = last.point(player)
                val previousGames = games.dropLast(1) + lastGameWithNewPoint
                val matchWithNewPoint = Match(
                    previousGames
                )
                if (lastGameWithNewPoint is Game.OngoingGame) {
                    matchWithNewPoint
                } else {
                    matchWithNewPoint.startNextGame()
                }
            }
        }
    }

    fun startNextGame(): Match {
        val initialServe = games.last().initialServe.next()
        val newGame = Game.OngoingGame(0, 0, initialServe, initialServe)
        return Match(
            games + newGame
        )
    }

    companion object {
        fun createNewMatch(): Match {
            val initialServe = Player.random()
            return Match(
                listOf(Game.OngoingGame(0, 0, initialServe, initialServe))
            )
        }
    }

    val score = games.filterIsInstance<Game.FinishedGame>().fold(Pair(0, 0)) { acc, current ->
        when (current.winner) {
            Player.Left -> Pair(acc.first + 1, acc.second)
            Player.Right -> Pair(acc.first, acc.second + 1)
        }
    }
}

sealed class Game(val pointsLeft: Int = 0, val pointsRight: Int = 0, val initialServe: Player) {
    internal val isDeuce = pointsLeft >= 10 && pointsRight >= 10

    class OngoingGame(
        pointsLeft: Int, pointsRight: Int, initialServe: Player, val serve: Player
    ) : Game(pointsLeft, pointsRight, initialServe) {
        private fun nextServe(): Player =
            if (isDeuce || (pointsLeft + pointsRight + 1) % 2 == 0) serve.next() else serve

        fun point(player: Player): Game {
            val nextPointsLeft = pointsLeft + if (player == Player.Left) 1 else 0
            val nextPointsRight = pointsRight + if (player == Player.Right) 1 else 0
            return if (!isDeuce && max(nextPointsRight, nextPointsLeft) == 11 || isDeuce && abs(
                    nextPointsRight - nextPointsLeft
                ) == 2
            ) FinishedGame(nextPointsLeft, nextPointsRight, initialServe)
            else OngoingGame(
                nextPointsLeft, nextPointsRight, initialServe, nextServe()
            )
        }
    }

    fun points(player: Player) = when (player) {
        Player.Left -> pointsLeft
        Player.Right -> pointsRight
    }

    class FinishedGame(pointsLeft: Int, pointsRight: Int, initialServe: Player) :
        Game(pointsLeft, pointsRight, initialServe) {
        val winner = if (pointsLeft > pointsRight) Player.Left else Player.Right
    }
}

class Matches(
    val players: Map<Player, String>,
    private val matches: List<Match>,
    private val undoPointer: Int,
    private val undoPointerAtStartOfUndo: Int
) {

    val canUndo = undoPointer > 1

    val canRedo =
        undoPointer > 0 && undoPointer <= matches.size && undoPointer < undoPointerAtStartOfUndo

    val current = matches.last()

    fun undo() = next(
        matches + matches[undoPointer - 2], undoPointer - 1, undoPointerAtStartOfUndo
    )

    fun redo() = next(
        matches + matches[undoPointer], undoPointer + 1, undoPointerAtStartOfUndo
    )

    fun point(player: Player) = next(matches + current.point(player), matches.size + 1)

    fun startNextGame() = next(matches + current.startNextGame(), matches.size + 1)

    private fun next(
        matches: List<Match>, undoPointer: Int, undopointerAtStartOfUndo: Int? = null
    ): Matches {
        return Matches(players, matches, undoPointer, undopointerAtStartOfUndo ?: undoPointer)
    }

    companion object {
        fun createMatches(left: String, right: String) = Matches(
            mapOf(Pair(Player.Left, left), Pair(Player.Right, right)),
            listOf(Match.createNewMatch()),
            1,
            1
        )
    }


    @Composable
    fun Debug() {
        Row(horizontalArrangement = spacedBy(4.dp)) {
            for (i in 0..matches.size) {
                Text(
                    fontWeight = boldIf(i == undoPointer),
                    text = if (i == undoPointerAtStartOfUndo) "<${i}>" else i.toString()
                )
            }
        }
    }

}

val uiWidth = 450.dp

@Composable
@Preview
fun App() {
    MaterialTheme {
        var matches: Matches? by remember { mutableStateOf(null) }

        Column {
            matches?.let { nonNullMatches ->
                Row(
                    modifier = Modifier.width(uiWidth),
                    horizontalArrangement = spacedBy(4.dp, alignment = Alignment.End)
                ) {
                    Button(
                        enabled = nonNullMatches.canUndo,
                        onClick = { matches = nonNullMatches.undo() },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(fontSize = 12.sp, text = "undo")
                    }
                    Button(
                        enabled = nonNullMatches.canRedo,
                        onClick = { matches = nonNullMatches.redo() },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(fontSize = 12.sp, text = "redo")
                    }
                }
                val aMatch = nonNullMatches.current
                Row {
                    Column(modifier = Modifier.width(uiWidth)) {
                        Row(horizontalArrangement = Arrangement.SpaceAround) {
                            Text(
                                modifier = Modifier.weight(0.4f),
                                textAlign = TextAlign.Center,
                                text = nonNullMatches.players.getValue(Player.Left)
                            )
                            Spacer(Modifier.weight(0.2f))
                            Text(
                                modifier = Modifier.weight(0.4f),
                                textAlign = TextAlign.Center,
                                text = nonNullMatches.players.getValue(Player.Right)
                            )
                        }
                        val finishedGames = aMatch.finishedGames
                        if (finishedGames.isNotEmpty()) {
                            finishedGames.forEach {
                                Score(it.pointsLeft, it.pointsRight)
                            }
                            Divider(thickness = 2.dp)
                            aMatch.score.let { (l, r) ->
                                Score(l, r)
                            }
                        }
                        Spacer(Modifier.height(30.dp))
                        if (aMatch.currentGame is Game.OngoingGame) Row(horizontalArrangement = Arrangement.SpaceBetween) {
                            GameControls(
                                aMatch, nonNullMatches.players, aMatch.currentGame
                            ) { player ->
                                matches = nonNullMatches.point(player)

                            }
                        }
                    }
                }

            } ?: Column {
                var left by remember { mutableStateOf("") }
                var right by remember { mutableStateOf("") }

                Row(horizontalArrangement = spacedBy(4.dp)) {
                    TextField(value = left, onValueChange = { left = it }, label = { Text("Left") })
                    TextField(value = right,
                        onValueChange = { right = it },
                        label = { Text("Right") })
                    Button(onClick = { matches = Matches.createMatches(left, right) }) {
                        Text("Start")
                    }

                }
            }
        }
    }
}


@Composable
private fun Score(left: Int, right: Int) {
    Row(horizontalArrangement = Arrangement.SpaceAround) {
        Text(
            modifier = Modifier.weight(0.4f),
            fontWeight = boldIf(left > right),
            textAlign = TextAlign.Center,
            text = left.toString()
        )
        Text(
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.2f), text = "-"
        )
        Text(
            modifier = Modifier.weight(0.4f),
            fontWeight = boldIf(right > left),
            textAlign = TextAlign.Center,
            text = right.toString()
        )
    }
}

private fun boldIf(b: Boolean) = if (b) FontWeight.Bold else FontWeight.Normal

@Composable
private fun RowScope.GameControls(
    match: Match,
    players: Map<Player, String>,
    game: Game.OngoingGame,
    point: (player: Player) -> Unit
) {
    val playersInOrder =
        (Player.entries.takeIf { match.reverseOrder }?.reversed() ?: Player.entries)
    PlayerButton(playersInOrder[0], players, game, point, modifier = Modifier.weight(0.4f))
    Text(
        modifier = Modifier.weight(0.2f).align(Alignment.CenterVertically),
        textAlign = TextAlign.Center,
        text = if (match.reverseOrder) "R" else ""
    )
    PlayerButton(playersInOrder[1], players, game, point, modifier = Modifier.weight(0.4f))
}

@Composable
private fun PlayerButton(
    player: Player,
    players: Map<Player, String>,
    game: Game.OngoingGame,
    point: (player: Player) -> Unit,
    modifier: Modifier
) {
    Box(modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min)) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            point(player)
        }) {
            if (game.serve == player) {
                Image(
                    modifier = Modifier.size(30.dp).align(Alignment.CenterVertically),
                    alignment = Alignment.CenterStart,
                    painter = painterResource(Res.drawable.paddle),
                    contentDescription = "paddle"
                )
            } else {
                Spacer(Modifier.size(30.dp))
            }

            Text("${players[player]} ${game.points(player)}")
            Spacer(Modifier.size(30.dp))
        }

    }
}

