package com.example.hscc_hsinwei.deviceposition_hw;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;

public class MainActivity extends AppCompatActivity {

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Context mContext;

    private DataOutputStream out_acc;
    private TextView text;
    private static ToggleButton tbtn;

    private boolean startRec = false;

    private Calendar calendar;

    private float[] gravityValues = null;
    private float[] magneticValues = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this.getApplicationContext();
        text = (TextView)findViewById(R.id.log);
        tbtn = (ToggleButton)findViewById(R.id.tbtn);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        // Show all supported sensor
        for(int i = 0; i < deviceSensors.size(); i++) {
            Log.d("[SO]", deviceSensors.get(i).getName());
        }

        if ((mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)) != null){
            mSensorManager.registerListener(mSensorListener, mSensor, 10000);
        } else {
            Toast.makeText(mContext, "ACCELEROMETER is not supported!", Toast.LENGTH_SHORT).show();
        }

        if ((mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)) != null){
            mSensorManager.registerListener(mSensorListener, mSensor, 10000);
        } else {
            Toast.makeText(mContext, "GYROSCOPE is not supported!", Toast.LENGTH_SHORT).show();
        }

        if ((mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) != null){
            mSensorManager.registerListener(mSensorListener, mSensor, 10000);
        } else {
            Toast.makeText(mContext, "MAGNETOMETER is not supported!", Toast.LENGTH_SHORT).show();
        }

        try {
            Log.d("[DIR]", Environment.getDataDirectory().getAbsolutePath());
            Log.d("[DIR]", Environment.getExternalStorageDirectory().getAbsolutePath());

            String dir_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AbsAccCollection";
            File dir = new File(dir_path);
            if(!dir.exists()) {
                dir.mkdir();
            }
            File file_acc = new File(dir, "raw_acc.txt");

            if (file_acc.exists()) {
                file_acc.delete();
            }

            out_acc = new DataOutputStream(new FileOutputStream(file_acc, true));

        } catch (Exception e) {
            e.printStackTrace();
        }

        /* get time */
        SimpleTimeZone pdt = new SimpleTimeZone(8 * 60 * 60 * 1000, "Asia/Taipei");
        calendar = new GregorianCalendar(pdt);

        tbtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startRec = true;
                    Date trialTime = new Date();
                    calendar.setTime(trialTime);
                    text.append(" Start time: " + calendar.get(Calendar.HOUR_OF_DAY) + ":" +
                            calendar.get(Calendar.MINUTE) + ":" +
                            calendar.get(Calendar.SECOND));
                } else {
                    startRec = false;
                    Date trialTime = new Date();
                    calendar.setTime(trialTime);
                    text.append(" End time: " + calendar.get(Calendar.HOUR_OF_DAY) + ":" +
                            calendar.get(Calendar.MINUTE) + ":" +
                            calendar.get(Calendar.SECOND));
                    try {
                        out_acc.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private SensorEventListener mSensorListener = new SensorEventListener(){

        public final void onSensorChanged(SensorEvent event) {
            String raw;
            String time;
            if(startRec) {
                if ((gravityValues != null) && (magneticValues != null)
                        && (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)) {
                    try {

                        float[] deviceRelativeAcceleration = new float[4];
                        deviceRelativeAcceleration[0] = event.values[0];
                        deviceRelativeAcceleration[1] = event.values[1];
                        deviceRelativeAcceleration[2] = event.values[2];
                        deviceRelativeAcceleration[3] = 0;

                        // Change the device relative acceleration values to earth relative values
                        // X axis -> East
                        // Y axis -> North Pole
                        // Z axis -> Sky

                        float[] R = new float[16], I = new float[16], earthAcc = new float[16];

                        SensorManager.getRotationMatrix(R, I, gravityValues, magneticValues);

                        float[] inv = new float[16];

                        android.opengl.Matrix.invertM(inv, 0, R, 0);
                        android.opengl.Matrix.multiplyMV(earthAcc, 0, inv, 0, deviceRelativeAcceleration, 0);
                        //Log.d("Acceleration", "Values: (" + earthAcc[0] + ", " + earthAcc[1] + ", " + earthAcc[2] + ")");
                        //text.append("Values: (" + earthAcc[0] + ", " + earthAcc[1] + ", " + earthAcc[2] + ")\n");

                        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
                        Date curDate = new Date(System.currentTimeMillis()); // 獲取當前時間
                        time = formatter.format(curDate);
                        raw = String.valueOf(earthAcc[0]) + "," + String.valueOf(earthAcc[1]) + "," + String.valueOf(earthAcc[2]) + ","
                                + time + "\n";
                        out_acc.write(raw.getBytes());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                    gravityValues = event.values;
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    magneticValues = event.values;
                }
            }
        }

        @Override
        public final void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something here if sensor accuracy changes.
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorListener);
    }
}
