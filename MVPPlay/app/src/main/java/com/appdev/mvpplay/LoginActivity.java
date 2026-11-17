package com.appdev.mvpplay;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.content.Intent;
public class LoginActivity extends AppCompatActivity {
    private TextView tabSignIn, tabCreate;
    private View layoutSignIn, layoutCreate;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        tabSignIn = findViewById(R.id.tabSignIn);
        tabCreate = findViewById(R.id.tabCreate);
        layoutSignIn = findViewById(R.id.layoutSignIn);
        layoutCreate = findViewById(R.id.layoutCreate);

        tabSignIn.setOnClickListener(v -> showSignIn(true));
        tabCreate.setOnClickListener(v -> showSignIn(false));
        setupPasswordToggle(findViewById(R.id.etPassword), findViewById(R.id.ivTogglePassword));
        setupPasswordToggle(findViewById(R.id.etCreatePassword), findViewById(R.id.ivToggleCreatePassword));
        findViewById(R.id.btnGuest).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });
    }
    private void showSignIn(boolean signIn) {
        layoutSignIn.setVisibility(signIn ? View.VISIBLE : View.GONE);
        layoutCreate.setVisibility(signIn ? View.GONE : View.VISIBLE);

        styleTab(tabSignIn, signIn);
        styleTab(tabCreate, !signIn);
    }

    private void setupPasswordToggle(EditText field, ImageView eye) {
        eye.setOnClickListener(v -> {
            boolean isHidden = field.getTransformationMethod() instanceof PasswordTransformationMethod;
            if (isHidden) {
                // show the password
                field.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                eye.setImageResource(R.drawable.ic_eye);
            } else {
                // hide the password
                field.setTransformationMethod(PasswordTransformationMethod.getInstance());
                eye.setImageResource(R.drawable.ic_eye_off);
            }
            // keep the cursor at the end of the text
            field.setSelection(field.getText().length());
        });
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_selected : 0);
        tab.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.button_text : R.color.text_secondary));
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }
}