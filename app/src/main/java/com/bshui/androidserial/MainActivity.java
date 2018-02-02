package com.bshui.androidserial;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {
    private TextView tvVersion;
    private Context mContext;
    private Button btFt;
    private Button btPl;
    private Button btSerial;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this.getApplicationContext();

        tvVersion = (TextView)findViewById(R.id.tvVersion);
        tvVersion.setText("版本号:V"+getVersion(mContext));
        tvVersion.setTextColor(Color.BLUE);
        btFt = (Button)findViewById(R.id.btft);
        btPl = (Button)findViewById(R.id.btpl);
        btSerial = (Button)findViewById(R.id.btSerial);

        //FT232
        btFt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,FdtiActivity.class));
            }
        });

        //PL2303HXD
        btPl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,
                        Pl2303Activity.class));
            }
        });

        //通用串口
        btSerial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,
                        SerialActivity.class));
            }
        });


    }

    public String getVersion(Context mContext){
        String VersionName="1.0";
        try {
            PackageInfo packageInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            VersionName = packageInfo.versionName;
        }catch (PackageManager.NameNotFoundException e){
            e.printStackTrace();
        }

        return VersionName;
    }
}
