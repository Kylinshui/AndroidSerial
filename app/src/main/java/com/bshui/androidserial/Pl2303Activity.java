package com.bshui.androidserial;

import android.app.Dialog;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.bshui.androidserial.util.Common;

import java.io.IOException;

import tw.com.prolific.driver.pl2303.PL2303Driver;

public class Pl2303Activity extends AppCompatActivity {
    private PL2303Driver mSerial;
    private static final String ACTION_USB_PERMISSION = "com.bshui.androidserial.USB_PERMISSION";
    private PL2303Driver.BaudRate baud = PL2303Driver.BaudRate.B115200;//波特率
    private PL2303Driver.DataBits dataBits= PL2303Driver.DataBits.D8;//8bit
    private PL2303Driver.StopBits stopBits = PL2303Driver.StopBits.S1;//1bit
    private PL2303Driver.Parity parity = PL2303Driver.Parity.NONE;//none
    private PL2303Driver.FlowControl flowControl =  PL2303Driver.FlowControl.OFF;//none

    private TextView tvPlstatus;


    private TextView tvSend;
    private TextView tvRecv;
    private Button   btPlSet;
    private Button   btSend;
    private Button   btClear;
    private ToggleButton tgbt;
    private EditText edRecv;
    private EditText edSend;
    private CheckBox checkhex;

    private int iavailable = 0;
    private int sendlength=0;
    private int recivelength=0;

    private lpreadThread read_thread;
    private Handler handler;
    private boolean bReadThreadGoing = false;
    private boolean isHex = false;
    byte[] readData;
    char[] readDataToText;
    private static final int readLength = 512;
    private String Hexdata="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fdti);

        tvPlstatus = (TextView)findViewById(R.id.tvfdtstatus);

        btSend  = (Button)findViewById(R.id.btSend);
        tvSend = (TextView)findViewById(R.id.tvSend);
        tvRecv = (TextView)findViewById(R.id.tvRecvCount);

        btPlSet = (Button)findViewById(R.id.btFtdiSet);
        btSend  = (Button)findViewById(R.id.btSend);
        btClear = (Button)findViewById(R.id.btClean);
        tgbt    = (ToggleButton) findViewById(R.id.tgbtrecive);

        edRecv  = (EditText)findViewById(R.id.edRecive);
        edSend  = (EditText)findViewById(R.id.edSend);

        checkhex = (CheckBox)findViewById(R.id.checkHex);

        readData = new byte[readLength];
        readDataToText = new char[readLength];

        mSerial = new PL2303Driver((UsbManager)getSystemService(Context.USB_SERVICE),
                this,ACTION_USB_PERMISSION);
        //check USB host function
        if(!mSerial.PL2303USBFeatureSupported()){
            Toast.makeText(this,
                    "不支持USB host API",Toast.LENGTH_LONG).show();

            mSerial = null;
            tvPlstatus.setText("串口状态:不支持usb host");
            return;
        }
        if(!mSerial.enumerate()){
            Toast.makeText(this,
                    "no more devices found",Toast.LENGTH_LONG)
                    .show();
        }


        try{
            //在进入界面后打开串口
            Thread.sleep(1500);
            startOpenDev();
        }catch (Exception e){
            e.printStackTrace();
        }

        btPlSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogSetting();
            }
        });

        //发送
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mSerial==null)
                    return;
                if(!mSerial.isConnected()){
                    Toast.makeText(getApplicationContext(),
                            "设备没有连接",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                String writeData = edSend.getText().toString().trim().replace(" ", "");;
                if(!isHex) {
                    //按ASCII来发送

                    //写数据
                    int res = mSerial.write(writeData.getBytes(), writeData.length());
                    if(res<0){
                        tvSend.setText("数据发送:失败" );
                        return;
                    }
                    sendlength += writeData.length();
                    tvSend.setText("数据发送:" + sendlength);
                }else{
                    //按十六进制数据来发送
                    boolean check = Common.isHex(writeData);
                    if(check){
                        //String 转hex
                        byte[] data = Common.hexStringToByteArray(writeData);
                        //写数据
                        mSerial.write(data,data.length);
                        sendlength +=data.length;
                        tvSend.setText("数据发送:" + sendlength);

                    }else{
                        Toast.makeText(getApplicationContext(),
                                "请输入十六进制数据,有效Hex组合(0-9,A-F,a-f)如:a2 00 10",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }
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

        tgbt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean ischecked) {

                bReadThreadGoing = ischecked;
                if(bReadThreadGoing){
                    read_thread = new lpreadThread();
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

    private class lpreadThread extends Thread{

        @Override
        public void run() {
            super.run();
            int i;


            while(bReadThreadGoing==true){
                try {
                    Thread.sleep(50);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }

                if(mSerial==null)
                    return;
                if(!mSerial.isConnected())
                    return;

                //iavilable读取的长度

                iavailable = mSerial.read(readData);

                if(iavailable<0)
                    return;
                if(iavailable > 0){


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

            }
        }
    }

    private void dialogSetting(){
        AlertDialog.Builder builder = new AlertDialog.Builder(Pl2303Activity.this);
        builder.setTitle("串口参数设置");
        builder.setIcon(R.drawable.setting);
        builder .setCancelable(false); //禁用返回键关闭对话框
        LayoutInflater inflater = LayoutInflater.from(Pl2303Activity.this);
        View v = inflater.inflate(R.layout.setting_dialog,null);
        Spinner spinner_baudrates = (Spinner)v.findViewById(R.id.spinner_boudrates);
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


        btFOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //确定设置所获取的参数
                //这里先设置成默认值
                // setDefConfig();
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

    private void startOpenDev(){
        if(mSerial==null){
            return;
        }
       // mSerial.InitByDefualtValue();
        if(mSerial.isConnected()){
            if(!mSerial.InitByBaudRate(baud, 700)){
                if(!mSerial.PL2303Device_IsHasPermission()){
                    Toast.makeText(this,
                            "打开失败,没有权限",
                            Toast.LENGTH_LONG).show();

                    tvPlstatus.setText("串口状态:打开失败");
                }
            }else{
                //打开成功
                tvPlstatus.setText("串口状态:打开");
                //默认配置
           try {
             mSerial.setup(baud, dataBits, stopBits, parity, flowControl);
             }catch (IOException e){
                 e.printStackTrace();
                }
            }
            bReadThreadGoing = true;

            read_thread = new lpreadThread();
            read_thread.start();
        }else{
            tvPlstatus.setText("串口状态:连接失败");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mSerial!=null)
            mSerial.end();
        bReadThreadGoing=false;
    }
}
