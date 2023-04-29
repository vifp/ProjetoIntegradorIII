package com.example.projeto3

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.math.log10
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var textDB: AppCompatTextView
    private lateinit var textAviso: AppCompatTextView
    private lateinit var imagemCheck: AppCompatImageView
    private lateinit var imagemAviso: AppCompatImageView
    private lateinit var botaoDoidera: AppCompatButton
    private var dataBase = Firebase.firestore

    private val recordAudioPermissionRequestCode = 1
    private val fineLocationPermissionRequestCode = 2
    private val referencia = 2e-5
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var locationManager: LocationManager
    private var lastDBs = mutableListOf<Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handler = Handler(Looper.getMainLooper())
        locationManager =
            getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager

        textDB = findViewById(R.id.textDB)
        textDB.text = 00.0.toString()
        botaoDoidera = findViewById(R.id.button)

        val audiopermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val locationpermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (audiopermission == PackageManager.PERMISSION_GRANTED) {
            if (locationpermission == PackageManager.PERMISSION_GRANTED) {
                startApp()
            } else {
                requestFineLocationPermission()
            }
        } else {
            requestRecordAudioPermission()
        }
    }

    // Thread principal
    private fun startApp() {
        emergencyButton()
        var spl: Double
        runnable = object : Runnable {
            override fun run() {
                spl = calcularSPL()
                generateAverage(spl)
                updateUI(spl)
                handler.postDelayed(this, 1000)
            }
        }
        Thread(runnable).start()
    }

    // Função da thread principal que muda os bagulho da UI
    private fun updateUI(spl: Double) {
        runOnUiThread {
            showText(spl)
            textColor(spl)
            notify(spl)
        }
    }

    // Quando fecha o app
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    // Permissão de gravação de áudio
    private fun AppCompatActivity.requestRecordAudioPermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    recordAudioPermissionRequestCode
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Permissão de localização precisa
    private fun AppCompatActivity.requestFineLocationPermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    fineLocationPermissionRequestCode
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Manipula a resposta do usuário às solicitações de permissão
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            recordAudioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestFineLocationPermission()
                } else {
                    Toast.makeText(
                        this,
                        "A permissão de gravação de áudio é necessária para o aplicativo funcionar corretamente.",
                        Toast.LENGTH_LONG
                    ).show()
                    requestRecordAudioPermission()
                }
            }
            fineLocationPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startApp()
                } else {
                    Toast.makeText(
                        this,
                        "A permissão de localização é necessária para o aplicativo funcionar corretamente.",
                        Toast.LENGTH_LONG
                    ).show()
                    requestFineLocationPermission()
                }
            }
        }
    }

    // Cálculo dos decibéis
    fun calcularSPL(): Double {
        // Configuaração da gravação de áudio
        val audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission()
        }
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            audioBufferSize
        )
        audioRecord.startRecording()

        // Captura de 1 segundo de áudio
        val audioSamples = ShortArray(sampleRate)
        var readSize = 0
        while (readSize < sampleRate) {
            readSize += audioRecord.read(audioSamples, readSize, sampleRate - readSize)
        }

        // Cálculo do nível de pressão sonora
        var rms = 0.0
        for (sample in audioSamples) {
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

    // Mostra o texto dos decibéis na tela com uma animação
    private fun showText(value: Double) {
        val oldValue = textDB.text.toString().replace(",", ".").toDouble()
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), value.toFloat())
        animator.duration = 1000
        animator.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Float
            if (textDB.text == "NaN") {
                textDB.text = "Pedro"
            } else {
                textDB.text = String.format("%.2f", currentValue.toDouble())
            }
        }
        animator.start()
    }

    // Notifica o usuário na tela caso o som ambiente for muito alto
    private fun notify(value: Double) {
        if (value >= 65.00) {
            imagemCheck = findViewById(R.id.imageViewCheck)
            imagemAviso = findViewById(R.id.imageViewWarning)
            textAviso = findViewById(R.id.textAviso)
            textAviso.visibility = VISIBLE
            textAviso.setText(R.string.text_notify)
            imagemCheck.visibility = INVISIBLE
            imagemAviso.visibility = VISIBLE
        } else if (::textAviso.isInitialized && textAviso.visibility == VISIBLE) {
            textAviso.visibility = INVISIBLE
            imagemAviso.visibility = INVISIBLE
            imagemCheck.visibility = VISIBLE
        }
    }

    // Muda a cor do texto de decibéis gradualmente conforme o valor muda
    private fun textColor(value: Double) {
        val lastColor: Int = textDB.currentTextColor
        val currentColor: Int = when (value) {
            in 00.00..64.99 -> ContextCompat.getColor(this, R.color.green)
            in 65.00..160.00 -> ContextCompat.getColor(this, R.color.red)
            else -> ContextCompat.getColor(this, R.color.light_blue)
        }
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), lastColor, currentColor)
        colorAnimation.duration = 1000
        colorAnimation.addUpdateListener { animation ->
            textDB.setTextColor(animation.animatedValue as Int)
        }
        colorAnimation.start()
    }

    // Cálculo da média
    private fun averageSPL(lastVals: List<Double>): Double {
        return lastVals.sum() / lastVals.size
    }

    // Gera a média dos últimos decibéis e verifica se passa um limiar x, se passar envia para o banco de dados
    private fun generateAverage(spl: Double) {
        if (lastDBs.size < 5)
            lastDBs.add(spl)
        else {
            val documentName = "Average"
            val id = UUID.randomUUID()
            val location = getCurrentLocation()
            val average = averageSPL(lastDBs)
            if (average > 30.00 && location != null) {
                val (latitude, longitude) = location
                val data = hashMapOf(
                    "Data" to FieldValue.serverTimestamp(),
                    "Média" to average.toFloat(),
                    "Latitude" to latitude,
                    "Longitude" to longitude
                )
                dataBase.collection("registers").document(documentName + id)
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Valor adicionado ao documento")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Erro ao adicionar o documento.", e)
                    }
            }
            lastDBs.clear()
        }
    }

    private fun getCurrentLocation(): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestFineLocationPermission()
            return null
        } else {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location != null) {
                val latitude = location.latitude.toString()
                val longitude = location.longitude.toString()
                return Pair(latitude, longitude)
            }
        }
        return null
    }


    // Configura o botão de emergência
    private fun emergencyButton() {
        botaoDoidera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestFineLocationPermission()
            } else {
                val documentName = "Alerts"
                val id = UUID.randomUUID()
                val location = getCurrentLocation()
                val alerta = "Perigo iminente"
                if (location != null) {
                    val (latitude, longitude) = location
                    val latitudeElongitude = hashMapOf(
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "Data" to FieldValue.serverTimestamp(),
                        "Alerta" to alerta
                    )

                    dataBase.collection("alerts").document(documentName + id)
                        .set(latitudeElongitude)
                        .addOnSuccessListener {
                            Log.d(
                                TAG,"Valor de latitude e longitude adicionado ao bd."
                            )
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Erro ao adicionar documento de latitude e longitude.", e)
                        }
                } else {
                    Toast.makeText(this, "Localização não pode ser obtida", Toast.LENGTH_SHORT)
                        .show()
                }

            }
        }
    }
}

// RENATo olha a localização de alerta no google rsrsrsrsrs TOdo
//pedro gay    TODO
//vai corintia TODO
//https://imgur.com/a/vFeNnME