package com.android.plugin;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.android.test.TestActivity;
import com.viewinject.annotation.MyBindView;
import com.viewinject.bindview.MyViewInjector;


public class MainActivity extends AppCompatActivity {

    @MyBindView(R.id.btn_view)
    Button btn_view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MyViewInjector.bindView(this);
        btn_view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TestActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MyViewInjector.unbindView(this);
    }
}