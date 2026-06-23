package com.presyohan.app.helper

import android.graphics.Canvas
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

interface ISwipeAdapter {
    fun isSwiped(position: Int): Boolean
    fun onItemSwiped(position: Int)
    fun closeSwipedItem()
}

interface ISwipeViewHolder {
    val foregroundCardView: View
    val backgroundActionCard: View
}

class SwipeRevealTouchHelper(
    private val adapter: ISwipeAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        return if (adapter.isSwiped(position)) {
            ItemTouchHelper.RIGHT
        } else {
            ItemTouchHelper.LEFT
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            if (direction == ItemTouchHelper.RIGHT) {
                adapter.closeSwipedItem()
            } else {
                adapter.onItemSwiped(position)
            }
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return 0.3f // 30% swipe triggers reveal
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * 5f // Prevent full fling off-screen
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is ISwipeViewHolder) {
            val foregroundView = viewHolder.foregroundCardView
            val backgroundCard = viewHolder.backgroundActionCard
            val maxSwipe = -backgroundCard.width.toFloat().coerceAtLeast(1f)
            val position = viewHolder.adapterPosition
            
            if (position != RecyclerView.NO_POSITION) {
                val translationX = if (adapter.isSwiped(position)) {
                    if (dX > 0) {
                        (maxSwipe + dX).coerceAtMost(0f)
                    } else {
                        maxSwipe
                    }
                } else {
                    if (dX < 0) {
                        dX.coerceAtLeast(maxSwipe)
                    } else {
                        0f
                    }
                }
                foregroundView.translationX = translationX
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is ISwipeViewHolder) {
            val position = viewHolder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                if (adapter.isSwiped(position)) {
                    val actionCardWidth = viewHolder.backgroundActionCard.width.toFloat()
                    viewHolder.foregroundCardView.translationX = if (actionCardWidth > 0) -actionCardWidth else -320f
                } else {
                    viewHolder.foregroundCardView.translationX = 0f
                }
            }
        }
    }
}
