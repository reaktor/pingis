package com.reaktor.rinkipingis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
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
    val leftPlayer: String, val rightPlayer: String, val games: List<Game>
) {
    fun player(player: Player) = when (player) {
        Player.Left -> leftPlayer
        Player.Right -> rightPlayer
    }

    val reverseOrder = games.size % 2 == 0

    fun point(player: Player): Match {
        return when (val last = games.last()) {
            is Game.FinishedGame -> throw (Exception("Can not add a point to a finished game"))
            is Game.OngoingGame -> {
                val lastGameWithNewPoint = last.point(player)
                val previousGames = games.dropLast(1) + lastGameWithNewPoint
                val matchWithNewPoint = Match(
                    leftPlayer, rightPlayer, previousGames
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
            leftPlayer, rightPlayer, games + newGame
        )
    }

    companion object {
        fun createNewMatch(leftPlayer: String, rightPlayer: String): Match {
            val initialServe = Player.random()
            return Match(
                leftPlayer, rightPlayer, listOf(Game.OngoingGame(0, 0, initialServe, initialServe))
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
    private val matches: List<Match>,
    private val undoPointer: Int,
    private val undopointerAtStartOfUndo: Int?
) {

    val canUndo = undoPointer > 1

    val canRedo =
        undoPointer > 0 && undoPointer <= matches.size && undoPointer < (undopointerAtStartOfUndo
            ?: 0)

    val current = matches.last()

    fun undo() = Matches(
        matches + matches[undoPointer - 2], undoPointer - 1, undopointerAtStartOfUndo
    )

    fun redo() = Matches(
        matches + matches[undoPointer], undoPointer + 1, undopointerAtStartOfUndo
    )

    fun point(player: Player): Matches {
        val newUndoPointer = matches.size + 1
        return Matches(matches + matches.last().point(player), newUndoPointer, newUndoPointer)
    }

    fun startNextGame(): Matches {
        val newUndoPointer = matches.size + 1
        return Matches(matches + matches.last().startNextGame(), newUndoPointer, newUndoPointer)
    }

    companion object {
        fun createMatches(leftPlayer: String, rightPlayer: String): Matches =
            Matches(listOf(Match.createNewMatch(leftPlayer, rightPlayer)), 1, null)
    }

    @Composable
    fun Debug() {
        Row(horizontalArrangement = spacedBy(4.dp)) {
            for (i in 0..matches.size) {
                Text(
                    fontWeight = boldIf(i == undoPointer),
                    text = if (i == undopointerAtStartOfUndo) "<${i}>" else i.toString()
                )
            }
        }
    }

}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var matches: Matches? by remember { mutableStateOf(null) }

        Column {
            @Suppress("ConstantConditionIf")
            if (false) matches?.Debug()
            matches?.let {
                Row(horizontalArrangement = spacedBy(4.dp, alignment = Alignment.End)) {
                    Button(enabled = it.canUndo, onClick = { matches = it.undo() }) {
                        Text("undo")
                    }
                    Button(enabled = it.canRedo, onClick = { matches = it.redo() }) {
                        Text("redo")
                    }
                }
            }



            matches?.let { nonNullMatches ->
                val aMatch = nonNullMatches.current
                Row {
                    Column(modifier = Modifier.width(300.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceAround) {
                            Text(modifier = Modifier.weight(0.4f), text = aMatch.leftPlayer)
                            Text(modifier = Modifier.weight(0.4f), text = aMatch.rightPlayer)
                        }
                        val finishedGames = aMatch.games.filterIsInstance<Game.FinishedGame>()
                        if (finishedGames.isNotEmpty()) {
                            finishedGames.forEach {
                                Score(it.pointsLeft, it.pointsRight)
                            }
                            Divider(thickness = 2.dp)
                            aMatch.score.let { (l, r) ->
                                Score(l, r)
                            }
                        }
                    }
                }

                when (val currentGame = aMatch.games.last()) {
                    is Game.OngoingGame -> Column {
                        GameControls(aMatch, currentGame) { player ->
                            matches = nonNullMatches.point(player)
                        }
                    }

                    is Game.FinishedGame -> Button(onClick = {
                        matches = nonNullMatches.startNextGame()
                    }) {
                        Text("Next game")
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
    Row {
        Text(
            modifier = Modifier.weight(0.4f),
            fontWeight = boldIf(left > right),
            text = left.toString()
        )
        Text(
            modifier = Modifier.weight(0.2f), text = "-"
        )
        Text(
            modifier = Modifier.weight(0.4f),
            fontWeight = boldIf(right > left),
            text = right.toString()
        )
    }
}

private fun boldIf(b: Boolean) = if (b) FontWeight.Bold else FontWeight.Normal

@Composable
private fun GameControls(
    match: Match, game: Game.OngoingGame, point: (player: Player) -> Unit
) {
    Row(horizontalArrangement = spacedBy(12.dp)) {
        (Player.entries.takeIf { match.reverseOrder }?.reversed() ?: Player.entries).forEach {
            PlayerButton(it, match, game, point)
        }

    }
}

@Composable
private fun PlayerButton(
    player: Player, match: Match, game: Game.OngoingGame, point: (player: Player) -> Unit
) {
    Row {
        Box(modifier = Modifier.width(20.dp)) {
            if (game.serve == player) {
                Image(
                    painter = painterResource(Res.drawable.paddle), contentDescription = "paddle"
                )
            }
        }
        Button(onClick = {
            point(player)
        }) {
            Text("${match.player(player)} ${game.points(player)}")
        }
    }
}

