package com.racks.parentalcontrol.child.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.racks.parentalcontrol.child.R;
import com.racks.parentalcontrol.child.remote.FirebaseClient;
import com.racks.parentalcontrol.child.interfaces.ErrorCallBack;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginName, etLoginemail, etLoginPassword;
    private Button btnLogin;
    private FirebaseClient firebaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        etLoginName = findViewById(R.id.etNameLogin);
        etLoginemail = findViewById(R.id.etEmailLogin);
        etLoginPassword = findViewById(R.id.etPasswordLogin);
        btnLogin = findViewById(R.id.btnLogin);
        firebaseClient = new FirebaseClient();
        btnLogin.setOnClickListener(view->{
            String name = Objects.requireNonNull(etLoginName.getText().toString().trim());
            String email = Objects.requireNonNull(etLoginemail.getText().toString().trim());
            String password = Objects.requireNonNull(etLoginPassword.getText().toString().trim());
            if(email.isEmpty() || password.isEmpty()){
                Toast.makeText(LoginActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                Toast.makeText(LoginActivity.this, "Please Enter a valid email", Toast.LENGTH_SHORT).show();
                return;
            }
            btnLogin.setText("Logging in...");
            btnLogin.setEnabled(false);
            login(name, email, password);
        });
        ImageView ivTogglePasswordRegister = findViewById(R.id.ivTogglePasswordLogin);
        ivTogglePasswordRegister.setOnClickListener(v ->
                togglePasswordVisibility(etLoginPassword, ivTogglePasswordRegister));
    }

    private void togglePasswordVisibility(EditText editText, ImageView icon) {
        if (editText.getInputType() ==
                (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            editText.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            icon.setImageResource(R.drawable.ic_visibility_off);
        } else {
            editText.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            icon.setImageResource(R.drawable.ic_visibility);
        }
        editText.setSelection(editText.getText().length());
    }

    private void login(String name, String email, String pass){
        firebaseClient.login(name, email, pass, () -> {
            btnLogin.setEnabled(true);
            btnLogin.setText("Login");
            goToMainActivity();
        }, err ->{
            Toast.makeText(LoginActivity.this, err, Toast.LENGTH_SHORT).show();
            btnLogin.setEnabled(true);
            btnLogin.setText("Login");
        });
    }

    private void goToMainActivity(){
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}