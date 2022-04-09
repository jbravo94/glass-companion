package com.iristick.smartglass.examples.sensorsreadout;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.Sensor;
import com.iristick.smartglass.core.SensorEvent;
import com.iristick.smartglass.core.SensorEventListener;
import com.iristick.smartglass.examples.BaseActivity;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.support.app.IristickApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This example listens to all available sensors on the Iristick smart glasses and shows their
 * measurements on the main Android screen.
 */
public class SensorsReadoutActivity extends BaseActivity {

    @Nullable private SensorListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensorsreadout_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setTitle(R.string.sensorsreadout_title);

        Headset headset = IristickApp.getHeadset();
        if (headset != null) {
            mAdapter = new SensorListAdapter(headset);
            ((ListView) findViewById(R.id.list)).setAdapter(mAdapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null)
            mAdapter.onResume();
    }

    @Override
    protected void onPause() {
        if (mAdapter != null)
            mAdapter.onPause();
        super.onPause();
    }

    private class SensorListAdapter extends BaseAdapter {
        @NonNull private final List<SensorItem> mItems;

        SensorListAdapter(@NonNull Headset headset) {
            /*
             * Find all sensors available on the smart glasses.
             * The TYPE_ALL argument is a wildcard to request all sensors.
             * If you want a specific sensor, you can use one of the following type constants:
             *
             * - TYPE_ACCELEROMETER
             *   The accelerometer measuring total acceleration (including gravity) on three axes,
             *   expressed in SI units (m/s²).
             *
             * - TYPE_GYROSCOPE
             *   The gyroscope measuring the rate of rotation in three axes, expressed in radians per second.
             *
             * - TYPE_MAGNETIC_FIELD
             *   The magnetometer measuring the ambient magnetic field in three axes, expressed in micro-Tesla.
             *
             * - TYPE_ROTATION_VECTOR
             *   A virtual sensor that combines the data from the accelerometer, the gyroscope and
             *   the magnetometer into a quaternion representing the absolute orientation of the smart glasses.
             */
            mItems = new ArrayList<>();
            for (Sensor sensor : headset.getSensorList(Sensor.TYPE_ALL)) {
                String units;
                switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    units = "m/s²";
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    units = "µT";
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    units = "rad/s";
                    break;
                default:
                    units = "";
                }
                mItems.add(new SensorItem(sensor, units));
            }
        }

        void onResume() {
            for (SensorItem sensor : mItems)
                sensor.onResume();
        }

        void onPause() {
            for (SensorItem sensor : mItems)
                sensor.onPause();
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public SensorItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null)
                view = LayoutInflater.from(SensorsReadoutActivity.this)
                        .inflate(R.layout.sensorsreadout_item, parent, false);
            SensorItem item = getItem(position);
            ((TextView)view.findViewById(R.id.title)).setText(item.getTitle());
            ((TextView)view.findViewById(R.id.values)).setText(item.getText());
            return view;
        }

        class SensorItem implements SensorEventListener {
            @NonNull private final Sensor mSensor;
            @NonNull private final String mUnits;
            @NonNull private float[] mValues;

            SensorItem(@NonNull Sensor sensor, @NonNull String units) {
                mSensor = sensor;
                mUnits = units;
                mValues = new float[0];
            }

            void onResume() {
                mSensor.registerListener(this, 500000, null);
            }

            void onPause() {
                mSensor.unregisterListener(this);
            }

            String getTitle() {
                return mSensor.toString();
            }

            String getText() {
                StringBuilder str = new StringBuilder();
                for (float v : mValues) {
                    if (str.length() != 0)
                        str.append('\n');
                    str.append(String.format(Locale.getDefault(), "% 7.2f %s", v, mUnits));
                }
                return str.toString();
            }

            @Override
            public void onSensorChanged(@NonNull SensorEvent event) {
                mValues = event.values;
                notifyDataSetChanged();
            }
        }
    }

}
