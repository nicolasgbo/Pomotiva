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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PerfilUsuarioFragment : Fragment() {

    private var _binding: FragmentPerfilUsuarioBinding? = null

    private var usersReference: DatabaseReference? = null
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
            usersReference = null
            Toast.makeText(context, "Erro ao acessar o Firebase Database", Toast.LENGTH_SHORT).show()
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
            // Tenta preencher a partir do Realtime Database primeiro; se não houver, usa displayName do Auth
            binding.registerEmailEditText.setText(userFirebase.email)

            val uid = userFirebase.uid
            try {
                val ref = usersReference
                if (ref == null) {
                    // Fallback para displayName do Auth
                    originalName = userFirebase.displayName
                    binding.registerNameEditText.setText(originalName)
                    updateActionButtonsState()
                } else {
                    // Único campo oficial: users/{uid}/name
                    ref.child(uid).child("name")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val rootName = snapshot.getValue(String::class.java)
                                originalName = rootName ?: userFirebase.displayName
                                binding.registerNameEditText.setText(originalName)
                                updateActionButtonsState()
                            }
                            override fun onCancelled(error: DatabaseError) {
                                originalName = userFirebase.displayName
                                binding.registerNameEditText.setText(originalName)
                                updateActionButtonsState()
                            }
                        })
                }
            } catch (e: Exception) {
                // Fallback em caso de erro ao acessar DB
                originalName = userFirebase.displayName
                binding.registerNameEditText.setText(originalName)
                updateActionButtonsState()
            }
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
                val current = auth.currentUser
                if (current == null) {
                    Toast.makeText(requireContext(), getString(R.string.profile_save_error), Toast.LENGTH_SHORT).show()
                    return@showConfirmDialog
                }
                val ref = usersReference
                if (ref == null) {
                    Toast.makeText(requireContext(), getString(R.string.profile_save_error), Toast.LENGTH_SHORT).show()
                    return@showConfirmDialog
                }

                val btn = binding.btnSaveChanges
                val nameInput = binding.registerNameEditText
                val btnLogout = binding.btnLogout
                val oldText = btn?.text
                btn?.isEnabled = false
                nameInput.isEnabled = false
                btnLogout.isEnabled = false
                btn?.text = "Carregando..."
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // 1) Salva no Realtime Database em users/{uid}/name (schema primário)
                        ref.child(current.uid).child("name").setValue(newName).await()

                        // 2) Atualiza também o displayName do FirebaseAuth
                        val request = UserProfileChangeRequest.Builder()
                            .setDisplayName(newName)
                            .build()
                        current.updateProfile(request).await()

                        // 3) Remover campo legado 'nome' se existir
                        try { ref.child(current.uid).child("nome").removeValue().await() } catch (_: Exception) { }

                        originalName = newName
                        updateActionButtonsState()
                        Toast.makeText(requireContext(), getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        btn?.isEnabled = true
                        nameInput.isEnabled = true
                        btnLogout.isEnabled = true
                        btn?.text = oldText
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
