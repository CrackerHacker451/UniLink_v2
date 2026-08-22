package com.example.unilink.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.unilink.R

class SwipeReplyCallback(
    context: Context,
    private val onSwipe: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private val replyIcon: Drawable? = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_revert)
    private var swipedPosition: Int = -1

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // We don't want to actually remove the item
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
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val limit = 150f
            val translationX = if (dX > limit) limit else dX
            
            itemView.translationX = translationX
            
            if (dX > 100f && isCurrentlyActive) {
                // Potential reply trigger
                swipedPosition = viewHolder.adapterPosition
            }

            // Draw Icon
            replyIcon?.let { icon ->
                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + icon.intrinsicHeight
                val iconLeft = itemView.left + 40
                val iconRight = iconLeft + icon.intrinsicWidth
                
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                icon.alpha = (translationX / limit * 255).toInt().coerceIn(0, 255)
                icon.draw(c)
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder.itemView.translationX >= 150f) {
            onSwipe(viewHolder.adapterPosition)
        }
        viewHolder.itemView.translationX = 0f
        super.clearView(recyclerView, viewHolder)
    }
}
