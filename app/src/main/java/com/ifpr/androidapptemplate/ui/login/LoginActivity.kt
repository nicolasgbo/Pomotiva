package com.ifpr.androidapptemplate.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.ifpr.androidapptemplate.MainActivity
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.ui.usuario.CadastroUsuarioActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var loadingOverlay: View
    private lateinit var loadingBrandText: TextView

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "signInWithEmail"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Impede captura de tela e gravação durante a exibição desta Activity
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        FirebaseApp.initializeApp(this)

        // Inicializa o Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        emailEditText = findViewById(R.id.edit_text_email)
        passwordEditText = findViewById(R.id.edit_text_password)
        loginButton = findViewById(R.id.button_login)
        registerLink = findViewById(R.id.registerLink)
        btnGoogleSignIn = findViewById<MaterialButton>(R.id.btnGoogleSignIn)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingBrandText = findViewById(R.id.loading_brand_text)

        val registerLink: TextView = findViewById(R.id.registerLink)
        val fullText = registerLink.text.toString()
        val linkText = "Cadastre-se"
        val start = fullText.indexOf(linkText)
        if (start >= 0) {
            val end = start + linkText.length
            val spannable = SpannableString(fullText)
            val clickable = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(applicationContext, CadastroUsuarioActivity::class.java)
                    startActivity(intent)
                }
            }
            spannable.setSpan(clickable, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(getColor(R.color.pomotiva_secondary)),
                start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            registerLink.text = spannable
            registerLink.movementMethod = LinkMovementMethod.getInstance()
            registerLink.highlightColor = 0x00000000
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            if (!validateInputs(email, password)) {
                // Não prossegue com login; garante que overlay não fique preso
                setLoading(false)
                return@setOnClickListener
            }
            setLoading(true)
            signIn(email.trim(), password)
        }

        // Configuration do Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Click handler do botão Google customizado (MaterialButton)
        btnGoogleSignIn.setOnClickListener {
            setLoading(true)
            signInGoogle()
        }

        // Aplica gradiente Pomotiva ao texto da marca no overlay
        applyBrandGradient()
    }


    private fun signIn(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    updateUI(firebaseAuth.currentUser)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
                setLoading(false)
            }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var valid = true
        val e = email.trim()
        val p = password

        if (e.isEmpty()) {
            emailEditText.error = "Informe o e-mail"
            emailEditText.requestFocus()
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            emailEditText.error = "E-mail inválido"
            emailEditText.requestFocus()
            valid = false
        } else {
            emailEditText.error = null
        }

        if (p.isEmpty()) {
            passwordEditText.error = "Informe a senha"
            if (valid) passwordEditText.requestFocus()
            valid = false
        } else if (p.length < 6) {
            passwordEditText.error = "A senha deve ter ao menos 6 caracteres"
            if (valid) passwordEditText.requestFocus()
            valid = false
        } else {
            passwordEditText.error = null
        }

        return valid
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // Navegue para a proxima atividade
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
        } else {
            Toast.makeText(
                applicationContext,
                "Email ou senha incorretos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun signInGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login bem-sucedido, navegar para a atividade principal ou atualizar UI
                    Log.d(TAG, "signInWithGoogle:success")
                    updateUI(firebaseAuth.currentUser)
                } else {
                    // Tratar falha de login
                    Log.w(TAG, "signInWithGoogle:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
                setLoading(false)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                // Tratar falha de login
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        setEnabledUI(!isLoading)
    }

    private fun setEnabledUI(enabled: Boolean) {
        emailEditText.isEnabled = enabled
        passwordEditText.isEnabled = enabled
        loginButton.isEnabled = enabled
        btnGoogleSignIn.isEnabled = enabled
        registerLink.isEnabled = enabled
    }

    private fun applyBrandGradient() {
        // Usa as cores de marca existentes (verde pausa e roxo foco)
        val startColor = getColor(R.color.pomotiva_primary)
        val endColor = getColor(R.color.pomotiva_secondary)
        val text = loadingBrandText.text.toString()
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