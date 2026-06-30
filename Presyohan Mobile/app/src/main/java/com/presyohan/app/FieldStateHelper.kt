package com.presyohan.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout

object FieldStateHelper {
    private val greyColor = Color.parseColor("#DADADA")
    private val darkGrey = Color.parseColor("#757575")

    fun setupFieldState(layout: TextInputLayout, editText: EditText, activeColor: Int) {
        // Disable default Material error text as it forces red outline
        layout.isErrorEnabled = false
        layout.isHelperTextEnabled = false

        // Focus change listener
        editText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            updateUIState(layout, editText, activeColor, hasFocus)
        }

        // TextWatcher to update on text change
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateUIState(layout, editText, activeColor, editText.hasFocus())
            }
        })

        // Initial state
        updateUIState(layout, editText, activeColor, false)
    }

    fun updateUIState(layout: TextInputLayout, editText: EditText, activeColor: Int, hasFocus: Boolean) {
        val hasText = editText.text.isNotEmpty()

        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_hovered),
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf()
        )

        if (hasFocus || hasText) {
            // Typing state or Done typing: Active color outline and hint for all states
            val colors = intArrayOf(activeColor, activeColor, activeColor, activeColor)
            layout.setBoxStrokeColorStateList(ColorStateList(states, colors))
            layout.hintTextColor = ColorStateList.valueOf(activeColor)
            layout.defaultHintTextColor = ColorStateList.valueOf(activeColor)
        } else {
            // Empty & unfocused: Back to grey outline and grey hint
            val colors = intArrayOf(activeColor, greyColor, greyColor, greyColor)
            layout.setBoxStrokeColorStateList(ColorStateList(states, colors))
            layout.hintTextColor = ColorStateList.valueOf(activeColor)
            layout.defaultHintTextColor = ColorStateList.valueOf(darkGrey)
        }
    }

    fun setErrorState(layout: TextInputLayout, editText: EditText, activeColor: Int) {
        // Keeps outline as active color if user has typed text, only grey when not typing yet
        updateUIState(layout, editText, activeColor, editText.hasFocus())
    }
}
