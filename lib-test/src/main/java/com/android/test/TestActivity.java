package com.android.test;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.viewinject.annotation.MyBindView;
import com.viewinject.bindview.MyViewInjector;

import androidx.appcompat.app.AppCompatActivity;

public class TestActivity extends AppCompatActivity {

    @MyBindView(resId = "btn_view")
    Button btn_view;
    @MyBindView(resId = "btn_test1")
    Button btn_test;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        MyViewInjector.bindView(this);
        btn_view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "btn_view", Toast.LENGTH_SHORT).show();
            }
        });
        btn_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "btn_test1", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
