package com.example.testapp

import android.os.Bundle
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.text = "Hello World"
        textView.textSize = 24f
        setContentView(textView)
    }
}
