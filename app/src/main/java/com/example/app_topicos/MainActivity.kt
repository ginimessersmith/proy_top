package com.example.app_topicos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executors

import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private val uuid = UUID.randomUUID().toString()
    private lateinit var recognizerIntent: Intent
    private var cliente: SessionsClient? = null
    private var sesion: SessionName? = null
    private lateinit var textToSpeech: TextToSpeech
    private val TAG = "MainActivity"
    private lateinit var sessionsClient: SessionsClient
    private lateinit var session: SessionName

    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val imageCapture by lazy { ImageCapture.Builder().build() }
    private val cameraExecutor = Executors.newSingleThreadExecutor()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Iniciar el servicio de sacudida
        val shakeServiceIntent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(shakeServiceIntent) // Para Android 8.0 y superior
        } else {
            startService(shakeServiceIntent) // Para versiones anteriores
        }
        // Inicializa TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Inicializa el reconocimiento de voz
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        // Verifica los permisos
        checkAudioPermission()

        // Configura el botón
        val btnSpeak: Button = findViewById(R.id.btnSpeak)
        btnSpeak.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Inicia el reconocimiento de voz
                    startListening()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Detiene el reconocimiento
                    speechRecognizer.stopListening()
                }
            }
            true
        }
    }

    private fun initializeDialogflow() {
        try {
            // Cargar las credenciales desde api.json en res/raw
            val stream = resources.openRawResource(R.raw.api)
            val credentials = GoogleCredentials.fromStream(stream)
            val settings = SessionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            // Crear el cliente de sesión
            sessionsClient = SessionsClient.create(settings)
            session = SessionName.of("tu-project-id", UUID.randomUUID().toString())

            Log.d("Dialogflow", "Inicialización exitosa")
        } catch (e: Exception) {
            Log.e("Dialogflow", "Error al inicializar Dialogflow: ${e.message}")
        }
    }

    // Función para enviar un mensaje a Dialogflow y obtener una respuesta
    fun sendToDialogflow(text: String) {
        try {
            val textInput = TextInput.newBuilder().setText(text).setLanguageCode("es").build()
            val queryInput = QueryInput.newBuilder().setText(textInput).build()

            val response = sessionsClient.detectIntent(session, queryInput)
            val replyText = response.queryResult.fulfillmentText

            Log.d("Dialogflow", "Respuesta: $replyText")
        } catch (e: Exception) {
            Log.e("Dialogflow", "Error al enviar mensaje: ${e.message}")
        }
    }

    private fun startListening() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Habla ahora", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0) ?: ""
                Log.d(TAG, "Texto reconocido: $spokenText")
                sendToDialogflow(spokenText)
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Error en SpeechRecognizer: $error")
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        speechRecognizer.startListening(recognizerIntent)
    }

    private fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("es", "ES")
        } else {
            Log.e(TAG, "Error al inicializar TextToSpeech")
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
    }

    private fun iniciarAsistente() {
        try {
            // Archivo JSON de configuración de la cuenta de Dialogflow (Google Cloud Platform)
            val config = resources.openRawResource(R.raw.api)

            // Leemos las credenciales de la cuenta de Dialogflow (Google Cloud Platform)
            val credenciales = GoogleCredentials.fromStream(config)

            // Leemos el 'projectId' el cual se encuentra en el archivo 'credenciales.json'
            val projectId = (credenciales as ServiceAccountCredentials).projectId

            // Construimos una configuración para acceder al servicio de Dialogflow (Google Cloud Platform)
            val generarConfiguracion = SessionsSettings.newBuilder()

            // Configuramos las sesiones que usaremos en la aplicación
            val configurarSesiones =
                generarConfiguracion.setCredentialsProvider(FixedCredentialsProvider.create(credenciales)).build()
            cliente = SessionsClient.create(configurarSesiones)
            sesion = SessionName.of(projectId, uuid)

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun iniciarAsistenteVoz() {

        textToSpeech = TextToSpeech(applicationContext,object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status != TextToSpeech.ERROR){
                    textToSpeech?.language=Locale("es")
                }
            }

        })

    }
    private fun initializeCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startPhotoTakingTask() {
        Log.d("CameraXApp", "Iniciando tarea de toma de fotos cada 2 segundos")
        val photoRunnable = object : Runnable {
            override fun run() {
                Log.d("CameraXApp", "Tomando foto en intervalo")
                takePhoto()
                handler.postDelayed(this, 2000) // Repite cada 2 segundos
            }
        }
        handler.post(photoRunnable)
    }

    private fun takePhoto() {
        Log.d("CameraXApp", "Intentando tomar foto")
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraXApp", "Foto guardada: ${photoFile.absolutePath}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Error al tomar foto: ${exception.message}", exception)
                }
            }
        )
    }
}
