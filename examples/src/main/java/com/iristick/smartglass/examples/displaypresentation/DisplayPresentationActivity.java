package com.iristick.smartglass.examples.displaypresentation;

import android.os.Bundle;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import com.iristick.smartglass.examples.BaseActivity;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.support.app.HudActivity;
import com.iristick.smartglass.support.app.HudPresentation;

/**
 * This example displays the current time on the display, using standard Android UI elements.
 */
public class DisplayPresentationActivity extends BaseActivity implements HudActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.displaypresentation_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setTitle(R.string.displaypresentation_title);
    }

    @Override
    public HudPresentation onCreateHudPresentation(@NonNull Display display) {
        return new ClockHud(this, display);
    }

}
