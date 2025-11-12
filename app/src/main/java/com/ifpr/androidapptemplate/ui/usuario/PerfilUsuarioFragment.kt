package com.ifpr.androidapptemplate.ui.usuario

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.databinding.FragmentPerfilUsuarioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PerfilUsuarioFragment : Fragment() {

    private var _binding: FragmentPerfilUsuarioBinding? = null

    private lateinit var usersReference: DatabaseReference
    private lateinit var auth: FirebaseAuth

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Estado original
    private var originalName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerfilUsuarioBinding.inflate(inflater, container, false)

        // Inicializa o Firebase Auth
        auth = FirebaseAuth.getInstance()

        try {
            usersReference = FirebaseDatabase.getInstance().getReference("users")
        } catch (e: Exception) {
            Log.e("DatabaseReference", "Erro ao obter referência para o Firebase DatabaseReference", e)
            // Trate o erro conforme necessário, por exemplo:
            Toast.makeText(context, "Erro ao acessar o Firebase DatabaseReference", Toast.LENGTH_SHORT).show()
        }

        // Acessar currentUser
        val user = auth.currentUser

        if (user != null) {
            binding.registerEmailEditText.isEnabled = false
        }

        user?.let {
            Glide.with(this)
                .load(it.photoUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .fallback(R.drawable.ic_avatar_placeholder)
                .circleCrop() //Deixando a img redonda
                .into(binding.userProfileImageView)
        }

        return binding.root
    }

    private fun signOut() {
        auth.signOut()
        Toast.makeText(
            context,
            "Logout realizado com sucesso!",
            Toast.LENGTH_SHORT
        ).show()

        requireActivity().finish()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Exibe os dados do usuario logado, se disponivel

        // Acessar currentUser
        val userFirebase = auth.currentUser
        if (userFirebase != null) {
            originalName = userFirebase.displayName
            binding.registerNameEditText.setText(originalName)
            binding.registerEmailEditText.setText(userFirebase.email)
        }

        // Estado inicial do botão salvar
        binding.btnSaveChanges?.isEnabled = false

        // TextWatcher para controlar estado sujo
        binding.registerNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateActionButtonsState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Salvar alterações: confirmação antes de aplicar
        binding.btnSaveChanges?.setOnClickListener {
            val newName = binding.registerNameEditText.text?.toString()?.trim()
            if (newName.isNullOrEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.profile_save_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showConfirmDialog(
                title = getString(R.string.profile_action_save),
                message = getString(R.string.profile_save_confirm_message),
                positiveText = getString(R.string.profile_action_yes),
                negativeText = getString(R.string.profile_action_no)
            ) {
                val request = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                auth.currentUser?.updateProfile(request)?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        originalName = newName
                        updateActionButtonsState()
                        Toast.makeText(requireContext(), getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.profile_save_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Logout com confirmação
        binding.btnLogout.setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.profile_logout_title),
                message = getString(R.string.profile_logout_message),
                positiveText = getString(R.string.profile_action_yes),
                negativeText = getString(R.string.profile_action_no)
            ) {
                signOut()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hasChanges(): Boolean {
        val currentName = binding.registerNameEditText.text?.toString()
        val nameChanged = currentName != (originalName ?: "")
        return nameChanged
    }

    private fun updateActionButtonsState() {
        val changed = hasChanges()
        binding.btnSaveChanges?.isEnabled = changed
    }

    private fun showConfirmDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String,
        onConfirm: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvMessage)
        val btnPositive = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPositive)
        val btnNegative = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNegative)

        tvTitle.text = title
        tvMessage.text = message
        btnPositive.text = positiveText
        btnNegative.text = negativeText

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnPositive.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        btnNegative.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
