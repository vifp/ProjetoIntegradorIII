package com.example.projeto3

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var textDB : AppCompatTextView
    private lateinit var textAviso : AppCompatTextView
    private lateinit var imagemCheck : AppCompatImageView
    private lateinit var imagemAviso : AppCompatImageView

    private val recordAudioPermissionRequestCode = 1
    private val referencia = 2e-5
    private val sampleRate = 44100
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var lastDBs = mutableListOf<Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.projeto3.R.layout.activity_main)
        handler = Handler(Looper.getMainLooper())

        textDB = findViewById(com.example.projeto3.R.id.textDB)
        textDB.text = 00.0.toString()

        val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if(permission == PackageManager.PERMISSION_GRANTED) {
            startApp()
        }
        else{
            requestRecordAudioPermission()
        }
    }

    private fun startApp(){
        runnable = object : Runnable {
            override fun run() {
                val spl = calcularSPL()
                generateAverage(spl)
                updateUI(spl)
                handler.postDelayed(this, 1000)
            }
        }
        Thread(runnable).start()
    }

    private fun generateAverage(spl: Double){
        if(lastDBs.size < 5)
            lastDBs.add(spl)
        else{
            val average = averageSPL(lastDBs)
            println(average)
            if(average > 65.00){
                // TODO Envia para o banco de dados etc...
            }
            lastDBs.clear()
        }
    }

    private fun updateUI(spl: Double){
        runOnUiThread {
            showText(spl)
            textColor(spl)
            notify(spl)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    // Manipular resposta do usuário às solicitações de permissão.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == recordAudioPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handler.post(runnable)
            } else {
                requestRecordAudioPermission()
            }
        }
    }

    // Calculo dos dB
    fun calcularSPL(): Double {
        // Configuaração da gravação de áudio
        val audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,audioFormat, audioBufferSize)
        audioRecord.startRecording()

        // Captura de 1 segundo de áudio
        val audioSamples = ShortArray(sampleRate)
        var readSize = 0
        while (readSize < sampleRate) {
            readSize += audioRecord.read(audioSamples, readSize, sampleRate - readSize)
        }

        // Cálculo do nível de pressão sonora
        var rms = 0.0
        for (sample in audioSamples){
            rms += sample * sample.toDouble()
        }
        rms = sqrt(rms / audioSamples.size)
        val db = 20 * log10(rms / referencia) - 94

        // Libera recursos de gravação de áudio
        audioRecord.stop()
        audioRecord.release()

        // Retorno do valor do SPL em decibéis
        return db
    }

    private fun AppCompatActivity.requestRecordAudioPermission() {
        try {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), recordAudioPermissionRequestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showText(value: Double)
    {
        val oldValue = textDB.text.toString().replace(",", ".").toDouble()
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), value.toFloat())
        animator.duration = 1000
        animator.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Float
            textDB.text = String.format("%.2f", currentValue.toDouble())
        }
        animator.start()
    }

    private fun notify(value: Double) {
        if (value >= 65.00) {
            imagemCheck = findViewById(com.example.projeto3.R.id.imageViewCheck)
            imagemAviso = findViewById(com.example.projeto3.R.id.imageViewWarning)
            textAviso = findViewById(com.example.projeto3.R.id.textAviso)
            textAviso.visibility = VISIBLE
            textAviso.setText(com.example.projeto3.R.string.text_notify)
            imagemCheck.visibility = INVISIBLE
            imagemAviso.visibility = VISIBLE
        } else if(::textAviso.isInitialized && textAviso.visibility == VISIBLE){
            textAviso.visibility = INVISIBLE
            imagemAviso.visibility = INVISIBLE
            imagemCheck.visibility = VISIBLE
        }
    }

    private fun textColor(value: Double)
    {
        val lastColor: Int = textDB.currentTextColor
        val currentColor: Int = when(value) {
            in 00.00..64.99  -> ContextCompat.getColor(this, com.example.projeto3.R.color.green)
            in 65.00..160.00 -> ContextCompat.getColor(this, com.example.projeto3.R.color.red)
            else -> ContextCompat.getColor(this, com.example.projeto3.R.color.light_blue)
        }
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), lastColor, currentColor)
        colorAnimation.duration = 1000
        colorAnimation.addUpdateListener { animation ->
            textDB.setTextColor(animation.animatedValue as Int)
        }
        colorAnimation.start()
    }

    private fun averageSPL(lastVals: List<Double>): Double {
        return lastVals.sum() / lastVals.size
    }
}