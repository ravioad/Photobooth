package com.example.photobooth

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.FileUtils
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), View.OnClickListener {

    val REQUEST_IMAGE1_CAPTURE = 1
    val REQUEST_IMAGE2_CAPTURE = 2
    private val images: ArrayList<String> = ArrayList()
    private var timer: CountDownTimer? = null
    lateinit var currentPhotoPath: String
    var isFirstImage: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tapToStartButton.setOnClickListener(this)
        capture_button.setOnClickListener(this)
        image1.setOnClickListener(this)
        image2.setOnClickListener(this)
        confirm_button.setOnClickListener(this)
        back_button.setOnClickListener(this)
    }

    private fun takePhotoAfterCountdown(requestCode: Int) {
        timer = object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds: Int = (millisUntilFinished / 1000).toInt()
                if (seconds > 0) {
                    countdownTextView.text = seconds.toString()
                }
            }

            override fun onFinish() {
                countdownLayout.visibility = View.VISIBLE
                captureLayout.visibility = View.GONE
                countdownTextView.visibility = View.GONE
                countdownTextView.text = ""
                dispatchTakePictureIntent(requestCode)
            }

        }
        timer!!.start()

    }

    private fun dispatchTakePictureIntent(requestCode: Int) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            intent.resolveActivity(packageManager)?.also {

                val photoFile: File? = try {
                    createImageFile()
                } catch (e: Exception) {
                    null
                }

                photoFile?.also {
                    val photoUri: Uri =
                        FileProvider.getUriForFile(this, "com.example.photobooth.fileprovider", it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(takePictureIntent, requestCode)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE1_CAPTURE && resultCode == RESULT_OK) {
            images.add(currentPhotoPath)
            countdownLayout.visibility = View.VISIBLE
            captureLayout.visibility = View.GONE
            countdownTextView.visibility = View.VISIBLE
            takePhotoAfterCountdown(REQUEST_IMAGE2_CAPTURE)
        }
        if (requestCode == REQUEST_IMAGE2_CAPTURE && resultCode == RESULT_OK) {
            images.add(currentPhotoPath)
            countdownLayout.visibility = View.GONE
            tapToStartLayout.visibility = View.GONE
            pickImageLayout.visibility = View.VISIBLE
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_DCIM)

        return File.createTempFile(
            "JPEG_${timeStamp}",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.tapToStartButton -> {
                countdownLayout.visibility = View.VISIBLE
                tapToStartLayout.visibility = View.GONE
            }
            R.id.capture_button -> {
                captureLayout.visibility = View.GONE
                countdownTextView.visibility = View.VISIBLE
                takePhotoAfterCountdown(REQUEST_IMAGE1_CAPTURE)
            }
            R.id.image1 -> {
                pickImageLayout.visibility = View.GONE
                confirmImageLayout.visibility = View.VISIBLE
                val bitmap = BitmapFactory.decodeFile(images[0])
                Glide.with(this).load(bitmap).into(image_preview)
                isFirstImage = true
            }
            R.id.image2 -> {
                pickImageLayout.visibility = View.GONE
                confirmImageLayout.visibility = View.VISIBLE
                val bitmap = BitmapFactory.decodeFile(images[1])
                Glide.with(this).load(bitmap).into(image_preview)
                isFirstImage = false
            }
            R.id.confirm_button -> {
                val intent = Intent(this@MainActivity, EditActivity::class.java).apply {
                    putExtra("image_path", if (isFirstImage!!) images[0] else images[1])
                }
                startActivity(intent)
                finish()
            }
            R.id.back_button -> {
                pickImageLayout.visibility = View.VISIBLE
                confirmImageLayout.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        if (timer != null) {
            timer?.cancel()
        }
        super.onDestroy()
    }

}