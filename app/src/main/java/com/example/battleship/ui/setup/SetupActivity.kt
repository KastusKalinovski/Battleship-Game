package com.example.battleship.ui.setup

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.battleship.R
import com.example.battleship.data.*
import com.example.battleship.internal.FieldOccupiedException
import kotlinx.android.synthetic.main.activity_setup.*
import java.lang.StrictMath.floor

class SetupActivity : AppCompatActivity() {
    private lateinit var board: Board
    private lateinit var boardAdapter: BoardGridAdapter
    private lateinit var shipAdapter: ShipListAdapter

    private var selectedShip: Ship? = null
    private lateinit var shipList: ArrayList<Ship>
    private var shipDirection = Orientation.VERTICAL

    private val player = Player("Dmitry", 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        initBoard()
        initShips()

        randomButton.setOnClickListener {
            generateRandomShips()
            randomButton.visibility = View.GONE;
            manualButton.visibility = View.GONE;
            startButton.visibility = View.VISIBLE;
        }

        manualButton.setOnClickListener {
            randomButton.visibility = View.GONE;
            manualButton.visibility = View.GONE;
            shipsLayout.visibility = View.VISIBLE;
        }

        rotateButton.setOnClickListener {
            if (shipDirection == Orientation.VERTICAL)
                shipDirection = Orientation.HORIZONTAL
            else
                shipDirection = Orientation.VERTICAL
            selectedShip?.orientation = shipDirection
        }

    }

    private fun initBoard() {
        board = Board()

        boardAdapter = BoardGridAdapter(this, board.getFieldStatus())
        { view: View, position: Int -> handleBoardClick(view, position) }

        boardGridView.adapter = boardAdapter
    }

    private fun initShips() {
        shipList = arrayListOf()
        shipList.add(Ship(ShipType.CARRIER))
        shipList.add(Ship(ShipType.CRUISER))
        shipList.add(Ship(ShipType.DESTROYER))
        shipList.add(Ship(ShipType.SUBMARINE))
        shipList.add(Ship(ShipType.BATTLESHIP))

        shipAdapter = ShipListAdapter(this, shipList)

        shipListView.adapter = shipAdapter

        shipListView.setOnItemClickListener { _, view, position, _ ->
            selectedShip = shipAdapter.getItem(position) as Ship
            selectedShip?.orientation = shipDirection
            shipAdapter.selectedPosition = position;
            shipAdapter.notifyDataSetChanged();
        }
    }

    private fun handleBoardClick(view: View, position: Int) {

        val x: Int = floor((position / board.boardX).toDouble()).toInt()
        val y: Int = position % board.boardX

        if (selectedShip != null) {

            val ship = selectedShip

            try {
                player.tryPlaceShip(board, ship!!, Coordinate(x, y))
                boardAdapter.refresh(board.getFieldStatus())

                selectedShip = null
                shipList.remove(ship)
                shipAdapter.selectedPosition = -1
                shipAdapter.notifyDataSetChanged()

            } catch (foe: FieldOccupiedException) {

                ship!!.coords.clear()
            }
        }
        //If all ships placed, start fucking game...
        if (shipList.isEmpty()){
            rotateButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE
        }
    }

    private fun generateRandomShips() {
        board = Board()
        player.generateShips(board)
        boardAdapter.refresh(board.getFieldStatus())
    }
}