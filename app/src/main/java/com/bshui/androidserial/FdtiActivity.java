package com.bshui.androidserial;

import android.app.Dialog;
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
import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;


public class FdtiActivity extends AppCompatActivity {
    private D2xxManager ftdid2xx;
    FT_Device ftDevice = null;
    private TextView tvFdtistatus;
    private TextView tvSend;
    private TextView tvRecv;
    private Button   btFtdiSet;
    private Button   btSend;
    private Button   btClear;
    private ToggleButton tgbt;
    private EditText edRecv;
    private EditText edSend;
    private CheckBox checkhex;

    private int iavailable = 0;
    private int sendlength=0;
    private int recivelength=0;
    byte[] readData;
    char[] readDataToText;
    private String Hexdata="";
    private static final int readLength = 512;
    private readThread read_thread;
    private Handler handler;
    private boolean bReadThreadGoing = false;
    private boolean isHex = false;
    private int baud = 115200;//波特率
    private byte dataBits = 8;//8bit
    private byte stopBits = 1;//1bit
    private byte parity = D2xxManager.FT_PARITY_NONE;//none
    private byte flowControl =  D2xxManager.FT_FLOW_NONE;//none
    D2xxManager.DriverParameters d2xxDrvParameter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fdti);

        tvFdtistatus = (TextView)findViewById(R.id.tvfdtstatus);
        tvSend = (TextView)findViewById(R.id.tvSend);
        tvRecv = (TextView)findViewById(R.id.tvRecvCount);

        btFtdiSet = (Button)findViewById(R.id.btFtdiSet);
        btSend  = (Button)findViewById(R.id.btSend);
        btClear = (Button)findViewById(R.id.btClean);
        tgbt    = (ToggleButton) findViewById(R.id.tgbtrecive);

        edRecv  = (EditText)findViewById(R.id.edRecive);
        edSend  = (EditText)findViewById(R.id.edSend);

        checkhex = (CheckBox)findViewById(R.id.checkHex);

        readData = new byte[readLength];
        readDataToText = new char[readLength];

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

        //发送
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ftDevice==null)
                    return;
               if(ftDevice.isOpen()==false){
                   Toast.makeText(getApplicationContext(),
                           "设备没有打开",
                           Toast.LENGTH_LONG).show();
                   return;
               }
                String writeData = edSend.getText().toString().trim().replace(" ", "");;
                  if(!isHex) {
                   //按ASCII来发送

                      //写数据
                      ftDevice.write(writeData.getBytes(), writeData.length());
                      sendlength += writeData.length();
                      tvSend.setText("数据发送:" + sendlength);
                  }else{
                   //按十六进制数据来发送
                    boolean check = Common.isHex(writeData);
                    if(check){
                        //String 转hex
                        byte[] data = Common.hexStringToByteArray(writeData);
                        //写数据
                        ftDevice.write(data,data.length);
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
        //参数设置
        btFtdiSet.setEnabled(false);
        btFtdiSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialogSetting();
            }
        });

        try {
            ftdid2xx = D2xxManager.getInstance(this);
        }catch (D2xxManager.D2xxException e){
            e.printStackTrace();
        }

        d2xxDrvParameter = new D2xxManager.DriverParameters();

        //startOpen Dev
        startOpenDev();

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
            Log.i("bshui","read Thread");

            while(bReadThreadGoing==true){
                try {
                    Thread.sleep(50);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }

                if(ftDevice==null)
                    return;


                    iavailable = ftDevice.getQueueStatus();

                    if(iavailable > 0){
                        if(iavailable > readLength){
                            iavailable = readLength;
                        }
                        ftDevice.read(readData, iavailable);
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

    //设置默认参数
    public void setDefConfig(){

        if (ftDevice.isOpen() == false) {

            return;
        }
        //reset to UART mode for 232 devices
        ftDevice.setBitMode((byte)0,D2xxManager.FT_BITMODE_RESET);
        ftDevice.setBaudRate(baud);
        ftDevice.setDataCharacteristics(dataBits,stopBits,parity);
        ftDevice.setFlowControl(flowControl,(byte)0x0b,(byte)0x0d);
    }
    private void dialogSetting(){
        AlertDialog.Builder builder = new AlertDialog.Builder(FdtiActivity.this);
        builder.setTitle("串口参数设置");
        builder.setIcon(R.drawable.setting);
        builder .setCancelable(false); //禁用返回键关闭对话框
        LayoutInflater inflater = LayoutInflater.from(FdtiActivity.this);
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


    public void startOpenDev(){
        int devCount = 0;
        devCount = ftdid2xx.createDeviceInfoList(getApplicationContext());
      //  Toast.makeText(this,
            //    "devCount:"+devCount,Toast.LENGTH_LONG).show();

        if(devCount > 0){
            D2xxManager.FtDeviceInfoListNode deviceList = ftdid2xx.getDeviceInfoListDetail(0);
            ftDevice = ftdid2xx.openByIndex(getApplicationContext(),
                    0,
                    d2xxDrvParameter);



            if(ftDevice.isOpen()){
                tvFdtistatus.setText("串口状态:打开");
                btFtdiSet.setEnabled(true);
                setDefConfig();
                bReadThreadGoing = true;
                read_thread = new readThread();
                read_thread.start();

            }else{
                tvFdtistatus.setText("串口状态:打开失败");
                btFtdiSet.setEnabled(false);
                //打开失败返回主界面

            }
        }else{
            //没有发现ZTE FT232设备关闭本activity
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(ftDevice!=null)
        ftDevice.close();
        bReadThreadGoing=false;
    }
}
