package com.example.battleship.ui.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.example.battleship.R
import com.example.battleship.data.models.Board
import com.example.battleship.data.models.Player
import com.example.battleship.data.models.Role
import com.example.battleship.data.models.Ship
import com.example.battleship.internal.getViewModel
import com.example.battleship.ui.MainActivity
import com.example.battleship.ui.MainActivity.Companion.ME_PLAYER
import com.example.battleship.ui.MainActivity.Companion.ROLE_NAME
import com.example.battleship.ui.MainActivity.Companion.ROOM_NAME
import com.example.battleship.ui.MainActivity.Companion.VS_PLAYER
import com.example.battleship.ui.setup.SetupActivity.Companion.BOARD
import com.example.battleship.ui.setup.SetupActivity.Companion.FLEET
import com.example.battleship.ui.setup.BoardGridAdapter
import kotlinx.android.synthetic.main.activity_game.*
import kotlin.math.floor

class GameActivity : AppCompatActivity() {

    companion object{
        const val result = "result"
        const val score = "score"
        const val role = "role"
        const val win = "win"
        const val x = "x"
        const val y = "y"
    }

    private lateinit var myBoardAdapter: BoardGridAdapter
    private lateinit var opponentBoardAdapter: BoardGridAdapter

    private val viewModel by lazy {
        getViewModel {
            val roomName = intent.getStringExtra(ROOM_NAME)!!
            val roleName = intent.getStringExtra(ROLE_NAME)!!
            val myPlayer = intent.getParcelableExtra<Player>(ME_PLAYER)!!
            val vsPlayer = intent.getParcelableExtra<Player>(VS_PLAYER)!!
            GameViewModel(roomName, roleName, myPlayer, vsPlayer) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        initObservers()

        initScores()
        initOwnBoardAdapter()
        initOpponentBoardAdapter()

        viewModel.initReferences()

        addShotListener()
        addScoreListener()
        addShotBackListener()

    }

    private fun initObservers() {
        viewModel.apply {
            refreshMyBoardLiveData.observe(this@GameActivity,
                Observer { board -> refreshMyBoard(board.fieldStatus) })
            refreshOpponentBoardLiveData.observe(this@GameActivity,
                Observer { board -> refreshOpponentBoard(board.fieldStatus) })
        }
    }

    private fun initScores() {
        scoreText.text = viewModel.vsPlayer.score.toString()
        myScoreText.text = viewModel.myPlayer.score.toString()
    }

    private fun initOwnBoardAdapter() {
        viewModel.myBoard = Board()

        viewModel.myBoard.fieldStatus = intent?.extras?.getSerializable(BOARD) as Array<Array<Int>>
        viewModel.myBoard.fleet = intent?.extras?.getSerializable(FLEET) as ArrayList<Ship>

        myBoardAdapter = BoardGridAdapter(this, viewModel.myBoard.fieldStatus)
        { view: View, position: Int -> handleBoardClick(view, position) }

        myBoardView.adapter = myBoardAdapter
    }

    private fun initOpponentBoardAdapter() {

        opponentBoardAdapter = BoardGridAdapter(this, viewModel.opponentBoard.fieldStatus)
        { view: View, position: Int -> handleBoardClick(view, position) }

        opponentBoardView.adapter = opponentBoardAdapter
    }

    private fun handleBoardClick(view: View, position: Int) {

        if ((view.parent as FrameLayout).parent == myBoardView) {

            return

        } else if (!viewModel.isOpponentBoardEnabled) {

            Toast.makeText(this, getString(R.string.not_your_turn), Toast.LENGTH_SHORT).show()
            return

        } else {

            val x: Int = floor((position / viewModel.opponentBoard.boardX).toDouble()).toInt()
            val y: Int = position % viewModel.opponentBoard.boardX

            viewModel.isOpponentBoardEnabled = false
            viewModel.sendCoordinate(x, y, viewModel.roleName)
        }
    }

    private fun addShotListener() {

        var shotListener = object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                if (dataSnapshot.value != null) {

                    //shot received
                    if (viewModel.isHost()) {

                        if (dataSnapshot.child(role).value.toString() == Role.GUEST.id.toString()) {

                            viewModel.isOpponentBoardEnabled = true

                            shotCoordinatesReceived(dataSnapshot)

                        }
                    } else {

                        if (dataSnapshot.child(role).value.toString() == Role.HOST.id.toString()) {

                            viewModel.isOpponentBoardEnabled = true

                            shotCoordinatesReceived(dataSnapshot)

                        }
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                this@GameActivity.viewModel.shotRef.setValue(viewModel.shot)
            }
        }
        this.viewModel.shotRef.addValueEventListener(shotListener)
    }

    private fun shotCoordinatesReceived(dataSnapshot: DataSnapshot) {

        val shipKilled = viewModel.shotCoordinatesReceived(dataSnapshot)

        if (!shipKilled.isBlank()) {

            Toast.makeText(this, "Ouch! $shipKilled has been destroyed!", Toast.LENGTH_LONG).show()

            val points: Int = viewModel.getShipPointsIfKill(dataSnapshot)

            viewModel.vsPlayer.addScore(points)
            viewModel.score = viewModel.vsPlayer.score
            scoreText.text =  viewModel.score.toString()

            if (viewModel.myBoard.isGameOver()) {
                viewModel.sendScore(viewModel.score, viewModel.roleName, 1)
                showMessage(getString(R.string.game_over), getString(R.string.game_over_subtitle))
            } else {
                viewModel.sendScore(viewModel.score, viewModel.roleName, 0)
            }
        }

    }

    private fun addShotBackListener() {

        var shotBackListener = object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                if (dataSnapshot.value != null) {

                    //shot results received
                    if (viewModel.isHost()) {

                        if (dataSnapshot.child(role).value.toString() == Role.GUEST.id.toString()) {

                            viewModel.shotBackCoordinatesReceived(dataSnapshot)

                        }
                    } else {

                        if (dataSnapshot.child(role).value.toString() == Role.HOST.id.toString()) {

                            viewModel.shotBackCoordinatesReceived(dataSnapshot)
                        }
                    }

                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                this@GameActivity.viewModel.shotRef.setValue(viewModel.shot)
            }
        }
        this.viewModel.shotBackRef.addValueEventListener(shotBackListener)
    }

    private fun addScoreListener() {

        var scoreListener = object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                if (dataSnapshot.value != null) {

                    //score received
                    if (viewModel.isHost()) {

                        if (dataSnapshot.child(role).value.toString() == Role.HOST.id.toString())

                            setScoreReceived(dataSnapshot)

                    } else {

                        if (dataSnapshot.child(role).value.toString() == Role.GUEST.id.toString())

                            setScoreReceived(dataSnapshot)

                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                this@GameActivity.viewModel.scoreRef.setValue(score)
            }
        }
        this.viewModel.scoreRef.addValueEventListener(scoreListener)
    }

    private fun setScoreReceived(dataSnapshot: DataSnapshot) {

        val score: String = dataSnapshot.child(score).value.toString()
        viewModel.myPlayer.score = score.toInt()
        myScoreText.text = score

        val win: Int = dataSnapshot.child(win).value.toString().toInt()
        if (win == 1) showMessage(getString(R.string.you_won), getString(R.string.you_won_subtitle))

    }

    private fun showMessage(title: String, subTitle: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(subTitle)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->

            viewModel.myPlayer.score = 0
            viewModel.vsPlayer.score = 0

            val intent = Intent(this@GameActivity, MainActivity::class.java)
            startActivity(intent)
        }

        builder.show()
    }

    private fun refreshMyBoard(fieldStatus: Array<Array<Int>>) {
        myBoardAdapter.refresh(fieldStatus)
    }

    private fun refreshOpponentBoard(fieldStatus: Array<Array<Int>>) {
        opponentBoardAdapter.refresh(fieldStatus)
    }

}