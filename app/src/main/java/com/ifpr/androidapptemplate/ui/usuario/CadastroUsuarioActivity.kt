package com.ifpr.androidapptemplate.ui.usuario

import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DatabaseReference
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.ui.login.LoginActivity


class CadastroUsuarioActivity  : AppCompatActivity() {
    private lateinit var textCadastroUsuarioTitle: TextView
    private lateinit var registerNameEditText: EditText
    private lateinit var registerEmailEditText: EditText
    private lateinit var registerPasswordEditText: EditText
    private lateinit var registerConfirmPasswordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var loginLink: TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingBrandText: TextView
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_usuario)

        // Inicializa o Firebase Auth
        auth = FirebaseAuth.getInstance()

        textCadastroUsuarioTitle = findViewById(R.id.textCadastroUsuarioTitle)
        registerNameEditText = findViewById(R.id.registerNameEditText)
        registerEmailEditText = findViewById(R.id.registerEmailEditText)
        registerPasswordEditText = findViewById(R.id.registerPasswordEditText)
        registerConfirmPasswordEditText = findViewById(R.id.registerConfirmPasswordEditText)
        registerButton = findViewById(R.id.salvarButton)
        loginLink = findViewById(R.id.loginLink)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingBrandText = findViewById(R.id.loading_brand_text)

        // Link "Entrar" com comportamento de clique
        val fullText = loginLink.text.toString()
        val linkText = "Entrar"
        val start = fullText.indexOf(linkText)
        if (start >= 0) {
            val end = start + linkText.length
            val spannable = android.text.SpannableString(fullText)
            val clickable = object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) {
                    // Volta para tela de login
                    finish()
                }
            }
            spannable.setSpan(clickable, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.UnderlineSpan(), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(getColor(R.color.pomotiva_secondary)),
                start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            loginLink.text = spannable
            loginLink.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            loginLink.highlightColor = 0x00000000
        }

        registerButton.setOnClickListener {
            if (!validateInputs()) return@setOnClickListener
            setLoading(true)
            createAccount()
        }

        // Gradiente Pomotiva no texto do overlay
        applyBrandGradient()
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        setEnabledUI(!isLoading)
    }

    private fun setEnabledUI(enabled: Boolean) {
        registerNameEditText.isEnabled = enabled
        registerEmailEditText.isEnabled = enabled
        registerPasswordEditText.isEnabled = enabled
        registerConfirmPasswordEditText.isEnabled = enabled
        registerButton.isEnabled = enabled
        loginLink.isEnabled = enabled
    }

    private fun validateInputs(): Boolean {
        var valid = true
        val name = registerNameEditText.text.toString().trim()
        val email = registerEmailEditText.text.toString().trim()
        val password = registerPasswordEditText.text.toString()
        val confirm = registerConfirmPasswordEditText.text.toString()

        if (name.isEmpty()) {
            registerNameEditText.error = "Informe o nome"
            if (valid) registerNameEditText.requestFocus()
            valid = false
        } else {
            registerNameEditText.error = null
        }

        if (email.isEmpty()) {
            registerEmailEditText.error = "Informe o e-mail"
            if (valid) registerEmailEditText.requestFocus()
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            registerEmailEditText.error = "E-mail inválido"
            if (valid) registerEmailEditText.requestFocus()
            valid = false
        } else {
            registerEmailEditText.error = null
        }

        if (password.length < 6) {
            registerPasswordEditText.error = "A senha deve ter ao menos 6 caracteres"
            if (valid) registerPasswordEditText.requestFocus()
            valid = false
        } else {
            registerPasswordEditText.error = null
        }

        if (confirm != password) {
            registerConfirmPasswordEditText.error = "As senhas não coincidem"
            if (valid) registerConfirmPasswordEditText.requestFocus()
            valid = false
        } else {
            registerConfirmPasswordEditText.error = null
        }

        return valid
    }

    private fun createAccount() {
        val name = registerNameEditText.text.toString().trim()
        val email = registerEmailEditText.text.toString().trim()
        val password = registerPasswordEditText.text.toString().trim()

        try {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Novo usuário cadastrado com sucesso!",
                            Toast.LENGTH_SHORT
                        ).show()
                        val user = auth.currentUser
                        updateProfile(user, name)
                        sendEmailVerification(user)
                    } else {
                        val errorMessage = task.exception?.message ?: "Erro desconhecido"
                        Log.e("FirebaseAuth", "Erro ao cadastrar usuário: $errorMessage")
                        Toast.makeText(
                            this,
                            "Falha ao cadastrar novo usuário: $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        } catch (ex: Exception) {
            setLoading(false)
            Log.e("FirebaseAuth", "Erro ao conectar com o Firebase", ex)
            Toast.makeText(
                this,
                "Falha ao conectar com o Firebase: ${ex.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sendEmailVerification(user: FirebaseUser?) {
        user?.sendEmailVerification()
            ?.addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "Verification email sent to ${user?.email}.",
                        Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(baseContext, "Failed to send verification email.",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateProfile(user: FirebaseUser?, displayName: String) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()

        user?.updateProfile(profileUpdates)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "Nome do usuario alterado com sucesso.",
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(baseContext, "Não foi possivel alterar o nome do usuario.",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun applyBrandGradient() {
        val startColor = getColor(R.color.pomotiva_primary)
        val endColor = getColor(R.color.pomotiva_secondary)
        val text = "Pomotiva"
        loadingBrandText.post {
            val width = loadingBrandText.paint.measureText(text)
            val shader: Shader = LinearGradient(
                0f, 0f, width, loadingBrandText.textSize,
                intArrayOf(startColor, endColor),
                null,
                Shader.TileMode.CLAMP
            )
            loadingBrandText.paint.shader = shader
            loadingBrandText.invalidate()
        }
    }
}