package com.example.photobooth

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yalantis.ucrop.UCrop
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import kotlinx.android.synthetic.main.activity_edit.*
import org.apache.commons.io.FileUtils
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class EditActivity : AppCompatActivity(), View.OnClickListener {

    private val RC_EXTERANL_STORAGE_PERM = 123
    lateinit var croppedPhotoPath: String
    lateinit var photoEditorView: PhotoEditorView
    lateinit var mPhotoEditor: PhotoEditor

    lateinit var imageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        val filepath = intent.getStringExtra("image_path")
        photoEditorView = findViewById(R.id.photoEditorView)
        draw.setOnClickListener(this)
        eraser.setOnClickListener(this)
        done_button.setOnClickListener(this)
        edit_button.setOnClickListener(this)
        undo.setOnClickListener(this)
        redo.setOnClickListener(this)
        addText.setOnClickListener(this)

        val uri: Uri = Uri.fromFile(File(filepath!!))
        UCrop.of(uri, Uri.fromFile(createImageFile("CroppedImages").absoluteFile))
            .withAspectRatio(9f, 8f)
            .withMaxResultSize(630, 580)
            .start(this)
    }

    private fun saveTask() {
        if (hasWriteStoragePermission()) {
            try {

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                mPhotoEditor.saveAsFile(
                    createImageFile("EditedImages").absolutePath,
                    object : PhotoEditor.OnSaveListener {
                        override fun onSuccess(imagePath: String) {
                            val bitmap2 = BitmapFactory.decodeFile(imagePath)
                            val resizedImage = Bitmap.createScaledBitmap(bitmap2, 650, 600, false)
                            val bitmap1 =
                                BitmapFactory.decodeResource(resources, R.drawable.photo_frame)
                            val finalBitmap = overlay(bitmap1, bitmap2)
                            storeImage(finalBitmap)
                            addImageToGallery()

                            Toast.makeText(this@EditActivity, "saved", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@EditActivity, MainActivity::class.java))
                            this@EditActivity.finish()
                        }

                        override fun onFailure(exception: java.lang.Exception) {
                            Toast.makeText(
                                this@EditActivity,
                                exception.message.toString(),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
            } catch (e: Exception) {

                Log.e("permissionCheckEx", e.message.toString())
            }
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.write_external_storage_perm),
                RC_EXTERANL_STORAGE_PERM,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun overlay(bitmap1: Bitmap, bitmap2: Bitmap): Bitmap {

        val bmOverlay = Bitmap.createBitmap(bitmap1.width, bitmap1.height, bitmap1.config)
        val canvas = Canvas(bmOverlay)
        canvas.drawBitmap(bitmap1, Matrix(), null)
        val left = ((bitmap1.width * 10) / 100).toFloat()
        val top = ((bitmap1.height * 15) / 100).toFloat()
        val right = ((bitmap1.width * 90) / 100).toFloat()
        val bottom = ((bitmap1.height * 85) / 100).toFloat()
        canvas.drawBitmap(bitmap2, null, RectF(left, top, right, bottom), null)
        return bmOverlay

    }

    private fun storeImage(image: Bitmap) {
        val file = createImageFile("EditedImages")
        Log.e("save_path", file.absolutePath.toString())
        try {
            val outputStream = FileOutputStream(file)
            image.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
        } catch (e: Exception) {
            Log.e("SavingError: ", e.message!!)
        }
    }

    private fun hasWriteStoragePermission(): Boolean {
        return EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            var bitmap: Bitmap? = null
            bitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(
                    this.contentResolver,
                    resultUri
                )
            } else {
                val source = ImageDecoder.createSource(this.contentResolver, resultUri!!)
                ImageDecoder.decodeBitmap(source)
            }
            photoEditorView.source.setImageBitmap(bitmap)
            mPhotoEditor = PhotoEditor.Builder(this, photoEditorView)
                .setPinchTextScalable(true).build()
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!);
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun addImageToGallery() {
        if (hasWriteStoragePermission()) {
            saveImage(this.contentResolver, "jpeg", File(croppedPhotoPath))
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.write_external_storage_perm),
                RC_EXTERANL_STORAGE_PERM,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(folderName: String): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? =
            getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/${folderName}")

        imageName = "IMG_${timeStamp}"

        return File.createTempFile(
            "Edited_${timeStamp}",
            ".jpg",
            storageDir
        ).apply {
            croppedPhotoPath = absolutePath
        }
    }

    private fun saveImage(cr: ContentResolver, imgType: String, filepath: File): Uri? {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, imageName)
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
        values.put(MediaStore.Images.Media.DESCRIPTION, "")
        values.put(MediaStore.Images.Media.MIME_TYPE, "images/${imgType}")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(MediaStore.Images.Media.DATA, filepath.toString())
        return cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun addTextDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val viewGroup: ViewGroup = findViewById(android.R.id.content)
        val dialogView: View =
            LayoutInflater.from(this).inflate(R.layout.custom_dialog, viewGroup, false)
        builder.setView(dialogView).setPositiveButton("OK") { dialog, id ->
            val editText = dialogView.findViewById<EditText>(R.id.editText)
            mPhotoEditor.addText(editText.text.toString(), Color.parseColor("#FFFFFF"))
        }.setNegativeButton("Cancel") { dialog, id ->
            dialog.cancel()
        }

        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()

    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.draw -> {
                mPhotoEditor.setBrushDrawingMode(true)
            }
            R.id.eraser -> {
                mPhotoEditor.brushEraser()
            }
            R.id.undo -> {
                mPhotoEditor.undo()
            }
            R.id.redo -> {
                mPhotoEditor.redo()
            }
            R.id.addText -> {
                addTextDialog()
            }
            R.id.done_button -> {
                if (done_button.text == getString(R.string.done_button_text)) {
                    showEditLayout(false)
                } else if (done_button.text == getString(R.string.confirm_button_text)) {
                    // save edited image
                    saveTask()
                }
            }
            R.id.edit_button -> {
                showEditLayout(true)
            }
        }
    }

    private fun showEditLayout(isVisible: Boolean) {
        if (isVisible) {
            draw.visibility = View.VISIBLE
            eraser.visibility = View.VISIBLE
            addText.visibility = View.VISIBLE
            undo.visibility = View.VISIBLE
            redo.visibility = View.VISIBLE
            edit_button.visibility = View.GONE
            done_button.text = getString(R.string.done_button_text)
        } else {
            draw.visibility = View.GONE
            eraser.visibility = View.GONE
            addText.visibility = View.GONE
            undo.visibility = View.GONE
            redo.visibility = View.GONE
            mPhotoEditor.setBrushDrawingMode(false)
            edit_button.visibility = View.VISIBLE
            done_button.text = getString(R.string.confirm_button_text)
        }
    }

    override fun onDestroy() {
        val pathDcim =
            "/storage/emulated/0/Android/data/com.example.photobooth/files/DCIM/"
        val pathCropped =
            "/storage/emulated/0/Android/data/com.example.photobooth/files/Pictures/CroppedImages"
        val dcimDir = File(pathDcim)
        val croppedDir = File(pathCropped)
        FileUtils.deleteDirectory(dcimDir)
        FileUtils.deleteDirectory(croppedDir)
        Log.e("deleteDebug: ", "directory deleted")
        super.onDestroy()
    }


}