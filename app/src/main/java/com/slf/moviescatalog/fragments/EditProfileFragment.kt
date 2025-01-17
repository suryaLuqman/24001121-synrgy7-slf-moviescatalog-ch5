package com.slf.moviescatalog.fragments

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.slf.moviescatalog.R
import com.slf.moviescatalog.ViewModel.UserViewModel
import com.slf.moviescatalog.databinding.FragmentEditProfileBinding
import com.slf.moviescatalog.model.User
import com.slf.moviescatalog.model.UserRepository
import com.slf.moviescatalog.ui.MainActivity
import com.slf.moviescatalog.utils.Constant
import com.slf.moviescatalog.utils.SharedHelper
import com.google.android.material.snackbar.Snackbar
import com.slf.moviescatalog.utils.DatePickerFragment
import com.slf.moviescatalog.utils.Validation
import kotlinx.android.synthetic.main.fragment_edit_profile.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


class EditProfileFragment : Fragment() {
    private var bind: FragmentEditProfileBinding? = null
    private val binding get() = bind!!
    private lateinit var userRepository: UserRepository
    private lateinit var shared: SharedHelper
    private lateinit var userViewModel: UserViewModel
    private var selectedImage: Uri? = null
    private var selectedBitmap: Bitmap? = null

    companion object {
        private const val REQUEST_CODE_IMAGE_PICKER = 2
        private const val REQUEST_CODE_PERMISSION = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bind = FragmentEditProfileBinding.inflate(inflater, container, false)
        userRepository = UserRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shared = SharedHelper(requireContext())
        userViewModel = ViewModelProvider(requireActivity())[UserViewModel::class.java]
        binding.apply {
            getDataProfile()

            btnEditSave.setOnClickListener {
                saveDataProfile()
            }

            bornedit.setOnClickListener { showDatePickerDialog() }

            photoedit.setOnClickListener {
                if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                    openImagePicker()
                } else {
                    requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSION)
                }
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICKER)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(intent, 2)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMAGE_PICKER && resultCode == Activity.RESULT_OK && data != null) {
            selectedImage = data.data
            context?.let {
                try {
                    if (selectedImage != null) {
                        // Salin gambar ke direktori internal
                        val internalUri = copyImageToInternalStorage(selectedImage!!)
                        if (internalUri != null) {
                            selectedImage = internalUri
                            selectedBitmap = if (Build.VERSION.SDK_INT >= 28) {
                                val source = ImageDecoder.createSource(it.contentResolver, internalUri)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                MediaStore.Images.Media.getBitmap(it.contentResolver, internalUri)
                            }
                            binding.photoedit.setImageBitmap(selectedBitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun copyImageToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream: InputStream? = context?.contentResolver?.openInputStream(uri)
            val file = File(context?.filesDir, "profile_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            outputStream.close()
            inputStream?.close()
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bind = null
    }

    private fun selectImage(it: View?) {
        activity?.let {
            if (ContextCompat.checkSelfPermission(it.applicationContext, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            } else {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(intent, 2)
            }
        }
    }

    private fun getDataProfile() {
        val username = shared.getString(Constant.USERNAME)
        getUser(username)
        binding.apply {
            userViewModel.user.observe(viewLifecycleOwner) { user ->
                Nameedit.setText(user.name)
                Emailedit.setText(user.email)
                bornedit.setText(user.born)
                addressedit.setText(user.address)

                // Debug log to check the URI
                Log.d("EditProfileFragment", "Profile photo URI: ${user.profilePhoto}")

                // Set the user's profile photo to the ImageButton
                if (user.profilePhoto != null) {
                    val imageUri = Uri.parse(user.profilePhoto)
                    try {
                        context?.contentResolver?.openInputStream(imageUri)?.close()
                        photoedit.setImageURI(imageUri)
                    } catch (e: Exception) {
                        Log.e("EditProfileFragment", "Error accessing URI: $imageUri", e)
                        photoedit.setImageResource(R.drawable.ic_photowhite) // Default drawable
                    }
                } else {
                    photoedit.setImageResource(R.drawable.ic_photowhite) // Default drawable
                }
            }
        }
    }

    private fun getUser(username: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = userRepository.getUser()
            runBlocking(Dispatchers.Main) {
                data?.let {
                    userViewModel.dataUser(it)
                }
            }
        }
    }

    fun showDatePickerDialog() {
        val newFragment = DatePickerFragment()
        newFragment.show(childFragmentManager, "datePicker")
    }

    private fun saveDataProfile() {
        binding.apply {
            when {
                Nameedit.text.isNullOrEmpty() -> Nameedit.error = "Fill the name"
                Emailedit.text.isNullOrEmpty() -> Emailedit.error = "Fill the email"
                !Validation.isValidEmail(Emailedit.text.toString()) -> Emailedit.error = "Enter a valid email"
                bornedit.text.isNullOrEmpty() -> bornedit.error = "Fill the age"
                addressedit.text.isNullOrEmpty() -> addressedit.error = "Fill the address"
                else -> {
                    userViewModel.user.observe(viewLifecycleOwner) { user ->
                        val newData = User(
                            user.id,
                            Nameedit.text.toString(),
                            selectedImage?.toString() ?: user.profilePhoto,
                            Emailedit.text.toString(),
                            bornedit.text.toString(),
                            addressedit.text.toString(),
                            user.username,
                            user.password
                        )

                        // Debug log to check the new profile photo URI
                        Log.d("EditProfileFragment", "Saving profile photo URI: ${newData.profilePhoto}")

                        lifecycleScope.launch(Dispatchers.IO) {
                            userRepository.saveUser(newData)
                            runBlocking(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Edit Profile Success", Toast.LENGTH_SHORT).show()
                                activity?.let {
                                    val intent = Intent(it, MainActivity::class.java)
                                    it.startActivity(intent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun onSNACK(view: View) {
        val snackbar = Snackbar.make(view, "Edit Profile Success", Snackbar.LENGTH_LONG).setAction("Action", null)
        snackbar.setActionTextColor(Color.BLACK)
        val snackbarView = snackbar.view
        snackbarView.setBackgroundColor(Color.LTGRAY)
        val textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text) as TextView
        textView.setTextColor(Color.WHITE)
        textView.textSize = 28f
        snackbar.show()
    }
}
