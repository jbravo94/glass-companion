package com.iristick.smartglass.examples.voicegrammar;

import android.os.Bundle;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.VoiceEvent;
import com.iristick.smartglass.core.VoiceGrammar;
import com.iristick.smartglass.examples.BaseActivity;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.support.app.IristickApp;

import java.util.Locale;

/**
 * This example shows how voice grammars may be used to listen to complex voice commands to pick a
 * date from January 1, 1900 to December 31, 2099.
 */
public class VoiceGrammarActivity extends BaseActivity implements VoiceEvent.Callback {

    private DatePicker mCalendar;
    private VoiceGrammar mGrammar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voicegrammar_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setTitle(R.string.voicegrammar_title);
        mCalendar = findViewById(R.id.calendar);
        mGrammar = buildGrammar();
    }

    @Override
    protected void onDestroy() {
        if (mGrammar != null) {
            mGrammar.release();
            mGrammar = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGrammar != null)
            IristickApp.startVoice(mGrammar);
        Headset headset = IristickApp.getHeadset();
        if (headset != null)
            headset.configureVoiceCommands(Headset.VOICE_FLAG_INHIBIT_VISUAL_FEEDBACK);
    }

    @Override
    protected void onPause() {
        if (mGrammar != null)
            IristickApp.stopVoice(mGrammar);
        Headset headset = IristickApp.getHeadset();
        if (headset != null)
            headset.configureVoiceCommands(0);
        super.onPause();
    }

    private VoiceGrammar buildGrammar() {
        VoiceGrammar.Builder builder = VoiceGrammar.Builder.create(this);
        builder.setCallback(this, null);
        final String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
        /* Different languages have their own method of saying dates and years.
         * For each supported language, a specific grammar is built. Tags are used to annotate the
         * different tokens in a uniform manner. Positive numbers denote (parts of) the year,
         * while negative numbers denote the month and day within the month.
         * The year is optional.
         *
         * Where possible, the years are split into smaller components in order to create a more
         * optimized grammar. The tag of each component represents its numerical contribution to
         * the year.
         * For example, in English, all years in the twentieth century start with 'nineteen', i.e.
         * 'nineteen o four' (1904), 'nineteen eighty-four' (1984) etc.
         */
        if (language.equals(Locale.FRENCH.getLanguage())) {
            builder.pushSequentialGroup(0, 1)
                     .addToken("le")
                   .popGroup()
                   .pushAlternativeGroup()
                     .addTokens(R.array.voicegrammar_days, -1, -1)
                   .popGroup()
                   .pushAlternativeGroup()
                     .addTokens(R.array.voicegrammar_months, -100, -100)
                   .popGroup()
                   /* Optional years, from 1900 to 2099 */
                   .pushSequentialGroup(0, 1)
                     .pushAlternativeGroup()
                       .addToken("1900", 1900)
                       .addToken("2000", 2000)
                     .popGroup()
                     .pushAlternativeGroup(0, 1);
            for (int i = 1; i < 100; ++i) builder.addToken(Integer.toString(i), i);
            builder  .popGroup()
                   .popGroup();
        } else {
            /* Fall-back to English */
            builder.pushAlternativeGroup()
                     .pushSequentialGroup()
                       .pushSequentialGroup(0, 1)
                         .addToken("the")
                       .popGroup()
                       .pushAlternativeGroup()
                         .addTokens(R.array.voicegrammar_days, -1, -1)
                       .popGroup()
                       .addToken("of")
                       .pushAlternativeGroup()
                         .addTokens(R.array.voicegrammar_months, -100, -100)
                       .popGroup()
                     .popGroup()
                     .pushSequentialGroup()
                       .pushAlternativeGroup()
                         .addTokens(R.array.voicegrammar_months, -100, -100)
                       .popGroup()
                       .pushSequentialGroup(0, 1)
                         .addToken("the")
                       .popGroup()
                       .pushAlternativeGroup()
                         .addTokens(R.array.voicegrammar_days, -1, -1)
                       .popGroup()
                     .popGroup()
                   .popGroup()
                   /* Optional years, from 1900 to 2099 */
                   .pushAlternativeGroup(0, 1)
                     .pushSequentialGroup()
                       .pushAlternativeGroup()
                         .addToken("19", 1900)
                         .addToken("20", 2000)
                       .popGroup()
                       .pushAlternativeGroup(0, 1)
                         .addToken("hundred")
                       .popGroup()
                       .pushAlternativeGroup(0, 1);
            for (int i = 1; i < 10; ++i) builder.addToken("o" + i, i);
            for (int i = 10; i < 100; ++i) builder.addToken(Integer.toString(i), i);
            builder    .popGroup()
                     .popGroup()
                     .pushSequentialGroup()
                       .addToken("2000", 2000)
                       .pushAlternativeGroup(0, 1)
                         .addToken("and")
                       .popGroup()
                       .pushAlternativeGroup(0, 1);
            for (int i = 1; i < 100; ++i) builder.addToken(Integer.toString(i), i);
            builder    .popGroup()
                     .popGroup()
                   .popGroup();
        }
        return builder.build();
    }

    @Override
    public void onVoiceEvent(@NonNull VoiceEvent event) {
        if (mCalendar == null)
            return;
        int year = 0;
        int month = 0;
        int day = 0;
        for (int tag : event.getTags()) {
            if (tag < 0 && tag >= -31)
                day = -tag;
            else if (tag <= -100 && tag >= -1200)
                month = -tag / 100;
            else
                year += tag;
        }
        if (year == 0) {
            /* No year given, default to the currently selected year. */
            year = mCalendar.getYear();
        }
        if (day != 0 && month != 0) {
            mCalendar.updateDate(year, month - 1, day);
        }
    }
}
