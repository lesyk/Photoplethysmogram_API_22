package io.github.kaisubr.photoplethysmogram_api_22;

import android.app.ProgressDialog;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button startButton;
    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        startButton = (Button) findViewById(R.id.button_start);

        startButton.setOnClickListener(new View.OnClickListener() { //lamdas not allowed using Java 7 (required for Instant Run)
            //instant-run: ctrl f10
            @Override
            public void onClick(View v) {
                ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                        "Loading PPG", true);

                Intent i = new Intent(MainActivity.this, PPGActivity.class);
                MainActivity.this.startActivity(i);

                dialog.dismiss();
                Log.i(TAG, "Opened activity_ppg successfully");

            }
        });
    }
}
