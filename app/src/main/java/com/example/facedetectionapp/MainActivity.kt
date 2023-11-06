package com.example.facedetectionapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }
    private fun init() {
        initTasks()
        initListeners()
    }

    private fun initTasks() {
        checkPermissions()
    }

    private fun checkPermissions() {
//        if(ContextCompat.checkSelfPermission(this, ))
    }

    private fun initListeners() {

    }
}