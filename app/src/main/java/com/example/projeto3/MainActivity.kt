package com.example.projeto3

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.location.Location
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
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.math.log10
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private lateinit var textSPL: AppCompatTextView
    private lateinit var alertText: AppCompatTextView
    private lateinit var checkImage: AppCompatImageView
    private lateinit var alertImage: AppCompatImageView
    private lateinit var emergencyButton: AppCompatButton

    private val recordAudioPermissionRequestCode = 1
    private val fineLocationPermissionRequestCode = 2
    private val reference = 2e-5
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val splUpdateMS = 1000L
    private val notifyThreshold = 65.00

    private lateinit var handler: Handler
    private lateinit var locationManager: LocationManager
    private var lastSPLs = mutableListOf<Double>()
    private var dataBase = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handler = Handler(Looper.getMainLooper())
        locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager

        initializeViews()
        configViews()

        // Inicializa o app se tiver as permissões necessárias
        if(hasRecordAudioPermission() && hasFineLocationPermission()){
            handler.postDelayed(SPLRunnable(), splUpdateMS) // Inicia o app
        }
        else {
            if(!hasRecordAudioPermission()){
                requestRecordAudioPermission()
            }
            else if(!hasFineLocationPermission()){
                requestFineLocationPermission()
            }
        }
    }

    private fun initializeViews(){
        textSPL = findViewById(R.id.textSPL)
        alertText = findViewById(R.id.alertText)
        checkImage = findViewById(R.id.imageViewCheck)
        alertImage = findViewById(R.id.imageViewWarning)
        emergencyButton = findViewById(R.id.button)
    }

    private fun configViews(){
        textSPL.text = 00.00.toString()
        alertText.setText(R.string.text_notify)
        emergencyButton()
    }

    // Classe interna que implementa o runnable
    private inner class SPLRunnable : Runnable {
        override fun run() {
            val spl = calculateSPL()
            generateAverage(spl)
            handler.postDelayed(this, splUpdateMS)
            updateUI(spl)
        }
    }

    // Atualiza a UI
    private fun updateUI(spl: Double) {
        showText(spl)
        textColor(spl)
        notify(spl)
    }

    // Quando fecha o app
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // Permissão de gravação de áudio
    private fun AppCompatActivity.requestRecordAudioPermission() {
        try {
            if (!hasRecordAudioPermission()) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), recordAudioPermissionRequestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Verifica se há permissão de gravação de áudio
    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // Permissão de localização precisa
    private fun AppCompatActivity.requestFineLocationPermission() {
        try {
            if (!hasFineLocationPermission()) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), fineLocationPermissionRequestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Verifica se há permissão de localização precisa
    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Manipula a resposta do usuário às solicitações de permissão
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            recordAudioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestFineLocationPermission()
                } else {
                    Toast.makeText(this, R.string.text_audio_permission_denied, Toast.LENGTH_LONG).show()
                    requestRecordAudioPermission()
                }
            }
            fineLocationPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    handler.postDelayed(SPLRunnable(), splUpdateMS)
                } else {
                    Toast.makeText(this, R.string.text_local_permission_denied, Toast.LENGTH_LONG).show()
                    requestFineLocationPermission()
                }
            }
        }
    }

    // Cálculo dos decibéis
    fun calculateSPL(): Double {
        // Configuaração da gravação de áudio
        val audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, audioBufferSize)
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
        val db = 20 * log10(rms / reference) - 94

        // Libera recursos de gravação de áudio
        audioRecord.stop()
        audioRecord.release()

        // Retorno do valor do SPL em decibéis
        return db
    }

    // Mostra o texto dos decibéis na tela com uma animação
    private fun showText(value: Double) {
        val oldValue = textSPL.text.toString().replace(",", ".").toDouble()
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), value.toFloat())
        animator.duration = 1000
        animator.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Float
            textSPL.text = String.format("%.2f", currentValue.toDouble())
        }
        animator.start()
    }

    // Notifica o usuário na tela caso o som ambiente for muito alto
    private fun notify(value: Double) {
        if (value >= notifyThreshold && alertText.visibility == INVISIBLE) {
            alertText.visibility = VISIBLE
            alertImage.visibility = VISIBLE
            checkImage.visibility = INVISIBLE
        } else if (value < notifyThreshold && alertText.visibility == VISIBLE) {
            alertText.visibility = INVISIBLE
            alertImage.visibility = INVISIBLE
            checkImage.visibility = VISIBLE
        }
    }

    // Muda a cor do texto de decibéis gradualmente conforme o valor muda
    private fun textColor(value: Double) {
        val lastColor: Int = textSPL.currentTextColor
        val currentColor: Int = when (value) {
            in 30.00..64.99 -> ContextCompat.getColor(this, R.color.green)
            in 65.00..160.00 -> ContextCompat.getColor(this, R.color.red)
            else -> ContextCompat.getColor(this, R.color.light_blue)
        }
        if (lastColor == currentColor) {
            return
        }
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), lastColor, currentColor)
        colorAnimation.duration = 1000
        colorAnimation.addUpdateListener { animation ->
            textSPL.setTextColor(animation.animatedValue as Int)
        }
        colorAnimation.start()
    }

    // Cálculo da média, retorna um texto formatado em apenas duas casas decimais
    private fun averageSPL(lastVals: List<Double>): String {
        val average = lastVals.sum() / lastVals.size
        return "%.2f".format(average)
    }

    // Verifica se o vetor dos últimos decibéis está cheio e calcula e envia a média para o banco de dados se estiver
    private fun generateAverage(spl: Double) {
        if (lastSPLs.size < 5)
            lastSPLs.add(spl)
        else {
            val id = UUID.randomUUID()
            val location = getCurrentLocation()
            val average = averageSPL(lastSPLs).replace(",", ".").toDouble()
            sendAverageToDB(average, location, id)
            lastSPLs.clear()
        }
    }

    // Envia a média para o banco de dados
    private fun sendAverageToDB(average: Double, location: Pair<String, String>?, id: UUID){
        if (average > 30.00 && location != null) {
            val (latitude, longitude) = location
            val geoPoint = GeoPoint(latitude.toDouble(), longitude.toDouble())
            val data = hashMapOf(
                "Data" to FieldValue.serverTimestamp(),
                "Média" to average,
                "Localização" to geoPoint
            )
            dataBase.collection("registers").document("Average $id").set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Média adicionada ao documento.")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Erro ao adicionar a média ao documento.", e)
                }
        }
    }

    private fun getCurrentLocation(): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestFineLocationPermission()
            return null
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            var location: Location? = null

            // Verificando se há sinal de gps ou de rede
            if (!isGpsEnabled && !isNetworkEnabled) {
                Toast.makeText(this, R.string.text_no_location_provider, Toast.LENGTH_SHORT).show()
                return null
            } else {
                if (isNetworkEnabled) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                if (isGpsEnabled) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }
                if (location != null) {
                    val latitude = location.latitude.toString()
                    val longitude = location.longitude.toString()
                    return Pair(latitude, longitude)
                }
            }
        }
        return null
    }

    // Configura o botão de emergência
    private fun emergencyButton() {
        val alertsCollection = dataBase.collection("alerts")
        emergencyButton.setOnClickListener {
            if (hasFineLocationPermission()) {
                val id = UUID.randomUUID()
                val location = getCurrentLocation()
                sendLocationToDB(location, alertsCollection, id)
            } else {
                requestFineLocationPermission()
            }
        }
    }

    // Envia a localização para o banco de dados
    private fun sendLocationToDB(location: Pair<String, String>?, alertsCollection: CollectionReference, id: UUID){
        if (location == null) {
            Toast.makeText(this, R.string.text_null_location, Toast.LENGTH_SHORT).show()
            return
        }
        val (latitude, longitude) = location
        val geoPoint = GeoPoint(latitude.toDouble(), longitude.toDouble())
        val data = hashMapOf(
            "Localização" to geoPoint,
            "Data" to FieldValue.serverTimestamp(),
            "Alerta" to "Perigo iminente"
        )
        alertsCollection.document("Alert $id").set(data)
            .addOnSuccessListener {
                Log.d("EmergencyButton", "Localização adicionada ao documento.")
            }
            .addOnFailureListener { e ->
                Log.w("EmergencyButton", "Erro ao adicionar a localização ao documento.", e)
            }
    }
}



// oi vini todo todotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodotodo
// pedro gay    TODO
// vai corintia TODO
// https://imgur.com/a/vFeNnME