package org.schabi.newpipe.settings.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class SignInActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private Button btLogin;
    private Button btRegister;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        btLogin = findViewById(R.id.buttonLogin);
        btRegister = findViewById(R.id.buttonRegister);
        etUsername = findViewById(R.id.usernameEditText);
        etPassword = findViewById(R.id.passwordEditText);

        btLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {

                final String enteredUsername = etUsername.getText().toString();
                final String enteredPassword = etPassword.getText().toString();

                if (authenticateUser(SignInActivity.this,
                        enteredUsername, enteredPassword)) {
                    // Successful
                    Toast.makeText(SignInActivity.this, "Login successful",
                                    Toast.LENGTH_SHORT)
                            .show();
                    final Intent intent = new Intent(SignInActivity.this,
                            MainActivity.class);
                    intent.putExtra("username", enteredUsername);
                    startActivity(intent);
                } else {
                    // Incorrect credentials
                    Toast.makeText(SignInActivity.this,
                            "Invalid username or password", Toast.LENGTH_SHORT).show();
                }
            }

        });

        btRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {

                final String username = etUsername.getText().toString().trim();
                final String password = etPassword.getText().toString().trim();

                // Check if username and password are not empty
                if (!username.isEmpty() && !password.isEmpty()) {

                    final boolean registrationSuccessful = registerUser(username, password);

                    if (registrationSuccessful) {

                        Toast.makeText(getApplicationContext(), "Registration successful",
                                Toast.LENGTH_SHORT).show();
                    } else {

                        Toast.makeText(getApplicationContext(), "Registration failed",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {

                    Toast.makeText(getApplicationContext(),
                            "Please enter both username and password",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean authenticateUser(final Context context,
                                     final String username, final String password) {
        try {
            final InputStream inputStream = context.getResources()
                    .openRawResource(R.raw.credentials);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            System.out.println(inputStream);
            while ((line = reader.readLine()) != null) {
                line = line.replace("*", "").trim();
                final String[] fields = line.split("\\|\\|");

                boolean usernameCorrect = false;
                boolean passwordCorrect = false;

                for (final String field : fields) {
                    final String[] keyValue = field.split("=");
                    if (keyValue.length == 2) {
                        final String key = keyValue[0].trim();
                        final String value = keyValue[1].trim();

                        if (key.equals("username") && value.equals(username)) {
                            usernameCorrect = true;
                        } else if (key.equals("password") && value.equals(password)) {
                            passwordCorrect = true;
                        }
                    }
                }

                if (usernameCorrect && passwordCorrect) {
                    return true;
                }
            }

            reader.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean registerUser(final String username, final String password) {
        try {

            final FileOutputStream fos = openFileOutput("credentials.txt",
                    Context.MODE_APPEND);


            final String newCredentials = "*username=" + username
                    + "||password=" + password + "*\n";

            fos.write(newCredentials.getBytes());

            fos.close();

            return true;
        } catch (final IOException e) {
            e.printStackTrace();

            return false;
        }
    }
}
