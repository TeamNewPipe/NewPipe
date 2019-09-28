package org.schabi.newpipe.fragments.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.auth.AuthService;
import org.schabi.newpipe.database.RemoteDatabase;
import org.schabi.newpipe.util.NavigationHelper;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class LoginFragment extends BaseFragment implements OnClickListener {

    private Button loginButton;
    private Button switchButton;
    private Button skipButton;
    private EditText usernameET;
    private EditText passwordET;
    private EditText confirmPasswordET;
    private TextInputLayout confirmPasswordLayout;
    private ProgressBar progressBar;

    @State
    protected boolean isSignup;

    private CompositeDisposable disposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        loginButton = rootView.findViewById(R.id.login_btn);
        switchButton = rootView.findViewById(R.id.switch_btn);
        skipButton = rootView.findViewById(R.id.skip_login_btn);
        usernameET = rootView.findViewById(R.id.login_username);
        passwordET = rootView.findViewById(R.id.login_password);
        confirmPasswordET = rootView.findViewById(R.id.confirm_password);
        confirmPasswordLayout = rootView.findViewById(R.id.confirm_password_layout);
        progressBar = rootView.findViewById(R.id.loading_progress_bar);

        setTitle(getString(R.string.account));

        if(isSignup){
            confirmPasswordLayout.setVisibility(View.VISIBLE);
            loginButton.setText(R.string.signup_btn);
            switchButton.setText(R.string.already_user);
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        loginButton.setOnClickListener(this);
        switchButton.setOnClickListener(this);
        skipButton.setOnClickListener(this);

        passwordET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                confirmPasswordLayout.setError(null);
            }
        });

        confirmPasswordET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                confirmPasswordLayout.setError(null);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.login_btn:
                if(isSignup){
                    signup();
                }else{
                    login();
                }
                break;
            case R.id.switch_btn:
                if(isSignup){
                    confirmPasswordLayout.setVisibility(View.GONE);
                    loginButton.setText(R.string.login_btn);
                    switchButton.setText(R.string.new_user);
                    isSignup = false;
                }else{
                    confirmPasswordLayout.setVisibility(View.VISIBLE);
                    loginButton.setText(R.string.signup_btn);
                    switchButton.setText(R.string.already_user);
                    isSignup = true;
                }
                break;
            case R.id.skip_login_btn:
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
                editor.putBoolean("skip_login", true);
                editor.apply();
                NavigationHelper.openMainActivity(getContext());
                break;
        }
    }

    private void login(){
        String username = usernameET.getText().toString();
        String password = passwordET.getText().toString();
        Context context = getContext().getApplicationContext();
        progressBar.setVisibility(View.VISIBLE);
        Disposable disposable = AuthService.getInstance(context).login(username, password).observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext().getApplicationContext(), "Logged in as " + username, Toast.LENGTH_SHORT).show();
            // sync data post login
            RemoteDatabase remoteDatabase = (RemoteDatabase) NewPipeDatabase.getInstance(context);
            remoteDatabase.sync().subscribe();
            // go to main activity
            NavigationHelper.openMainActivity(getContext());
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext().getApplicationContext(), "Login failed" , Toast.LENGTH_SHORT).show();
        });
        disposables.add(disposable);
    }

    private void signup(){
        String username = usernameET.getText().toString();
        String password = passwordET.getText().toString();
        String confirmPassword = confirmPasswordET.getText().toString();
        if(!password.equals(confirmPassword)){
            confirmPasswordLayout.setError("password does not match!");
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        Context context = getContext().getApplicationContext();
        Disposable disposable = AuthService.getInstance(context).signup(username, password).observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext().getApplicationContext(), "Logged in as " + username, Toast.LENGTH_SHORT).show();
            // sync data post login
            RemoteDatabase remoteDatabase = (RemoteDatabase) NewPipeDatabase.getInstance(context);
            remoteDatabase.sync().subscribe();
            // go to main activity
            NavigationHelper.openMainActivity(getContext());
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext().getApplicationContext(), "Signup failed" , Toast.LENGTH_SHORT).show();
        });
        disposables.add(disposable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.clear();
        disposables = null;
    }
}