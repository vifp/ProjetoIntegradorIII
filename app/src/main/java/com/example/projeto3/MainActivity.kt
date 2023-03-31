package com.example.projeto3

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build.VERSION_CODES.R
import android.os.Bundle
import android.os.Handler
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10

class MainActivity : AppCompatActivity() {

    private lateinit var textTeste : AppCompatTextView
    private lateinit var textAviso : AppCompatTextView

    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 1
    private val referencia = 2e-5

    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT

    val handler = Handler()
    lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.projeto3.R.layout.activity_main)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission()
        }
        runnable = object: Runnable {
            override fun run() {
                var spl: Double = calcularSPL()
                textTeste = findViewById(com.example.projeto3.R.id.textTESTE)
                textTeste.setText(spl.toString())
                runOnUiThread {
                    textColor(spl)
                    notify(spl)
                }
                handler.postDelayed(this, 1000)
            }
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

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handler.post(runnable)
            } else {
                // Todo
            }
        }
    }

    // Calculo dos dB

    fun calcularSPL(): Double {

        // Configuaração da gravação de áudio;
        val audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG,AUDIO_FORMAT, audioBufferSize)
        audioRecord.startRecording()

        // Captura de 1 segundo de áudio:
        val audioSamples = ShortArray(SAMPLE_RATE)
        var readSize = 0
        while (readSize < SAMPLE_RATE) {
            readSize += audioRecord.read(audioSamples, readSize, SAMPLE_RATE - readSize)
        }

        // Cálculo do nível de pressão sonora:
        var rms = 0.0
        for (sample in audioSamples){
            rms += sample * sample.toDouble()
        }
        rms = Math.sqrt(rms / audioSamples.size)
        val db = 20 * log10(rms / referencia) - 94

        //Libera recursos de gravação de áudio
        audioRecord.stop()
        audioRecord.release()

        // Retorno do valor do SPL em decibéis:
        return db
    }

    fun AppCompatActivity.requestRecordAudioPermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun notify(value: Double) {
        textColor(value)
        if (value >= 65.00) {
            textAviso = findViewById(com.example.projeto3.R.id.textAviso)
            textAviso.visibility = VISIBLE
        } else {
            //
        }
    }

    fun textColor(value: Double)
    {
        when(value) {
            in 50.0..100.00  -> textTeste.setTextColor(ContextCompat.getColor(this, com.example.projeto3.R.color.yellow))
            in 100.0..200.00 -> textTeste.setTextColor(ContextCompat.getColor(this, com.example.projeto3.R.color.red))
            else -> textTeste.setTextColor(ContextCompat.getColor(this, com.example.projeto3.R.color.blue))
        }
    }
}