package com.alexchurkin.scsremote;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class HelpActivity extends Activity implements OnClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.help);
        Button button1 = findViewById(R.id.downloadServerButton);
        button1.setOnClickListener(this);
        Button button2 = findViewById(R.id.doneButton);
        button2.setOnClickListener(this);
    }

    public void onClick(View v) {
        if (v == findViewById(R.id.doneButton)) {
            finish();
        } else {
            String url = "http://sites.google.com/site/accelerometermouse/download-server";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
