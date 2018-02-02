package com.bshui.androidserial;

import android.app.Dialog;
import android.os.Handler;
import android.os.Message;
import android.serialport.api.SerialPort;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.bshui.androidserial.util.Common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SerialActivity extends AppCompatActivity {
    private Button btSerialSet;
    private Button btSend;
    private Button btClear;

    private TextView tvStatus;
    private TextView tvSend;
    private TextView tvRecv;

    private String serialnum;
    private int baudrate;
    private SerialPort mSerialPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;

    private ToggleButton tgbt;
    private EditText edRecv;
    private EditText edSend;
    private CheckBox checkhex;

    private int iavailable = 0;
    private int sendlength=0;
    private int recivelength=0;


    private Handler handler;
    private boolean bReadThreadGoing = false;
    private boolean isHex = false;
    byte[] readData;
    char[] readDataToText;
    private static final int readLength = 512;
    private String Hexdata="";
    private readThread read_thread;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fdti);
        btSerialSet = (Button)findViewById(R.id.btFtdiSet);
        btSend = (Button)findViewById(R.id.btSend);
        btClear = (Button)findViewById(R.id.btClean);
        edSend = (EditText)findViewById(R.id.edSend);
        edRecv = (EditText)findViewById(R.id.edRecive);
        tgbt    = (ToggleButton) findViewById(R.id.tgbtrecive);
        checkhex = (CheckBox)findViewById(R.id.checkHex);
        tvStatus = (TextView)findViewById(R.id.tvfdtstatus);
        tvSend   = (TextView)findViewById(R.id.tvSend);
        tvRecv   = (TextView)findViewById(R.id.tvRecvCount);


        dialogSetting();

        readData = new byte[readLength];
        readDataToText = new char[readLength];

        btSerialSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogSetting();
            }
        });
        tgbt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean ischecked) {

                bReadThreadGoing = ischecked;
                if(bReadThreadGoing){
                    read_thread = new readThread();
                    read_thread.start();
                }
            }
        });

        checkhex.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean ischecked) {
                Log.i("bshui","ischecked:"+ischecked);
                isHex = ischecked;
            }
        });
        //清屏
        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                edRecv.setText("");
                sendlength=0;
                recivelength=0;
                tvRecv.setText("数据接收:0");
                tvSend.setText("数据发送:0");
            }
        });

        //发送操作
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String writeData = edSend.getText().toString().trim().replace(" ", "");

                try {
                    if (mOutputStream != null) {

                        if(!isHex) {
                            //按ASCII来发送
                            mOutputStream.write(writeData.getBytes());
                            sendlength += writeData.length();
                            tvSend.setText("发送数据:"+sendlength);

                        }else{
                            //按十六进制数据来发送
                            boolean check = Common.isHex(writeData);
                            if(check){
                                //String 转hex
                                byte[] data = Common.hexStringToByteArray(writeData);
                                //写数据
                                mOutputStream.write(data);
                                sendlength +=data.length;
                                tvSend.setText("数据发送:" + sendlength);

                            }else{
                                Toast.makeText(getApplicationContext(),
                                        "请输入十六进制数据,有效Hex组合(0-9,A-F,a-f)如:a2 00 10",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                        }



                    } else {
                        return;
                    }

                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });

        //主线程通过Handler显示接收到的数据
        handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if(iavailable > 0){
                    if(!isHex) {
                        edRecv.append(String.copyValueOf(readDataToText, 0, iavailable));
                        recivelength += iavailable;
                        tvRecv.setText("数据接收:" + recivelength);
                    }else{
                        edRecv.append(Hexdata);
                        recivelength += Hexdata.length()/2;
                        tvRecv.setText("数据接收:" + recivelength);
                    }
                }
            }
        };
    }

    private class readThread extends Thread{

        @Override
        public void run() {
            super.run();
            int i;


            while(bReadThreadGoing==true){
                try{
                    Thread.sleep(50);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }

                try{
                    if(mInputStream == null)
                        return;

                    iavailable = mInputStream.read(readData);
                    if(iavailable > 0) {
                        if(!isHex) {
                            //字符串显示
                            for (i = 0; i < iavailable; i++) {
                                readDataToText[i] = (char) readData[i];
                            }
                        }else{
                            //readData是byte[] 转HexString
                            Hexdata = Common.BytestoHexString(readData, iavailable);
                        }

                        Message msg = handler.obtainMessage();
                        handler.sendMessage(msg);
                    }
                }catch (IOException e){
                    e.printStackTrace();
                    return;
                }

            }
        }
    }


    private void dialogSetting(){
        AlertDialog.Builder builder = new AlertDialog.Builder(SerialActivity.this);
        builder.setTitle("串口参数设置");
        builder.setIcon(R.drawable.setting);
        builder .setCancelable(false); //禁用返回键关闭对话框
        LayoutInflater inflater = LayoutInflater.from(SerialActivity.this);
        View v = inflater.inflate(R.layout.serial_dialog,null);
        final Spinner spinner_serial   =  (Spinner)v.findViewById(R.id.spinner_serial);
        final Spinner spinner_baudrates = (Spinner)v.findViewById(R.id.spinner_boudrates);
        Spinner spinner_datas     = (Spinner)v.findViewById(R.id.spinner_dataBits);
        Spinner spinner_parity    = (Spinner)v.findViewById(R.id.spinner_parity);
        Spinner spinner_flow      = (Spinner)v.findViewById(R.id.spinner_flowControl);
        Button  btFOk    = (Button)v.findViewById(R.id.btFOk);
        Button  btCancel = (Button)v.findViewById(R.id.btFCancel);
        builder.setView(v);

        final Dialog dialog = builder.create();
        //弹出坐标

        dialog.getWindow().setGravity(Gravity.CENTER);
        dialog.show();
        spinner_serial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                serialnum = (String)spinner_serial.getSelectedItem();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        spinner_baudrates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                baudrate = Integer.parseInt((String)spinner_baudrates.getSelectedItem());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        btFOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //确定设置所获取的参数
                //这里先设置成默认值
                // 设置串口号和波特率

                Log.i("bshui","port:"+serialnum+
                " baudrate:"+baudrate);
                try {
                    mSerialPort = new SerialPort(new File(serialnum), baudrate);
                    mOutputStream = mSerialPort.getOutputStream();
                    mInputStream  = mSerialPort.getInputStream();
                    bReadThreadGoing = true;
                    read_thread = new readThread();
                    read_thread.start();

                    btSend.setEnabled(true);
                    tvStatus.setText("串口状态:打开");
                }catch (IOException e){
                    e.printStackTrace();
                    tvStatus.setText("串口状态:打开失败");
                    btSend.setEnabled(false);

                }
                dialog.dismiss();

            }
        });

        btCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bReadThreadGoing=false;
        mSerialPort.close();
    }
}
