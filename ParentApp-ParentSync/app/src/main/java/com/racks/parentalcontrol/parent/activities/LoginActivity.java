package com.racks.parentalcontrol.parent.activities;


import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;


public class LoginActivity extends AppCompatActivity {

    private ViewFlipper viewFlipper;
    private Button btnLogin;
    private Button btnRegister;

    private static final int LOGIN_INDEX = 0;
    private static final int REGISTER_INDEX = 1;
    private FirebaseClient firebaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        viewFlipper = findViewById(R.id.viewFlipper);
        firebaseClient = new FirebaseClient(new MySharedPreferences(this));
        setupLoginScreen();
        setupRegisterScreen();
        setupBackPressHandler();
    }

    private void setupLoginScreen() {
        TextView tvRegisterNow = findViewById(R.id.tvRegisterNow);
        tvRegisterNow.setOnClickListener(v -> switchToRegister());

        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> {
            EditText etEmail = findViewById(R.id.etEmailLogin);
            EditText etPassword = findViewById(R.id.etPasswordLogin);
            String email = etEmail.getText().toString();
            String password = etPassword.getText().toString();
            if(email.isEmpty() || password.isEmpty()){
                Toast.makeText(LoginActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                Toast.makeText(LoginActivity.this, "Please Enter a valid email", Toast.LENGTH_SHORT).show();
                return;
            }
            if(password.length()<5){
                Toast.makeText(LoginActivity.this, "Password must be at least 5 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            btnLogin.setText("Logging in...");
            btnLogin.setEnabled(false);
            login(email, password);
        });

        EditText etPasswordLogin = findViewById(R.id.etPasswordLogin);
        ImageView ivTogglePasswordLogin = findViewById(R.id.ivTogglePasswordLogin);
        ivTogglePasswordLogin.setOnClickListener(v ->
                togglePasswordVisibility(etPasswordLogin, ivTogglePasswordLogin));
    }

    private void setupRegisterScreen() {
        TextView tvLoginNow = findViewById(R.id.tvLoginNow);
        tvLoginNow.setOnClickListener(v -> switchToLogin());

        btnRegister = findViewById(R.id.btnRegister);
        btnRegister.setOnClickListener(v -> {
            EditText etFullName = findViewById(R.id.etFullName);
            EditText etEmail = findViewById(R.id.etEmailRegister);
            EditText etPassword = findViewById(R.id.etPasswordRegister);
            EditText etConfirmPassword = findViewById(R.id.etConfirmPassword);

            String name = etFullName.getText().toString();
            String email = etEmail.getText().toString();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();
            if(name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()){
                Toast.makeText(LoginActivity.this, "Please fill all the fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                Toast.makeText(LoginActivity.this, "Please Enter a valid email", Toast.LENGTH_SHORT).show();
                return;
            }
            if(password.length()<5){
                Toast.makeText(LoginActivity.this, "Password must be at least 5 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            if(!password.equals(confirmPassword)){
                Toast.makeText(LoginActivity.this, "Password and Confirm Password must be same", Toast.LENGTH_SHORT).show();
                return;
            }
            btnRegister.setText("Registering...");
            btnRegister.setEnabled(false);
            register(name, email, password);
        });

        EditText etPasswordRegister = findViewById(R.id.etPasswordRegister);
        ImageView ivTogglePasswordRegister = findViewById(R.id.ivTogglePasswordRegister);
        ivTogglePasswordRegister.setOnClickListener(v ->
                togglePasswordVisibility(etPasswordRegister, ivTogglePasswordRegister));

        EditText etConfirmPassword = findViewById(R.id.etConfirmPassword);
        ImageView ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        ivToggleConfirmPassword.setOnClickListener(v ->
                togglePasswordVisibility(etConfirmPassword, ivToggleConfirmPassword));
    }

    private void switchToRegister() {
        viewFlipper.setInAnimation(this, R.anim.slide_in_right);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_left);
        viewFlipper.setDisplayedChild(REGISTER_INDEX);
    }

    private void switchToLogin() {
        viewFlipper.setInAnimation(this, R.anim.slide_in_left);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_right);
        viewFlipper.setDisplayedChild(LOGIN_INDEX);
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

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewFlipper.getDisplayedChild() == REGISTER_INDEX) {
                    switchToLogin();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void login(String email, String pass){
        firebaseClient.login(email, pass, () -> {
            btnLogin.setEnabled(true);
            btnLogin.setText("Login");
            goToMainActivity();
        }, err ->{
            Toast.makeText(LoginActivity.this, err, Toast.LENGTH_SHORT).show();
            btnLogin.setEnabled(true);
            btnLogin.setText("Login");
        });
    }

    private void register(String name, String email, String pass){
        firebaseClient.register(name, email, pass, () -> {
            btnRegister.setEnabled(true);
            btnRegister.setText("Register");
            goToMainActivity();
        }, err -> {
            Toast.makeText(LoginActivity.this, err, Toast.LENGTH_SHORT).show();
            btnRegister.setEnabled(true);
            btnRegister.setText("Register");
        });
    }

    private void goToMainActivity(){
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
