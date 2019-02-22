package com.example.dmitrirabinowitz.test_project;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.sourcepoint.cmplibrary.ConsentLib;
import com.sourcepoint.cmplibrary.ConsentLibException;

import java.util.HashSet;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private SharedPreferences sharedPref;

    private ConsentLib buildConsentLib(Activity activity) throws ConsentLibException {
        return ConsentLib.newBuilder()
                .setActivity(activity)
                // required, must be set second used to find account
                .setAccountId(22)
                // required, must be set third used to find scenario
                .setSiteName("mobile.demo")
                // optional, used for running stage campaigns
                .setStage(true)
                // optional, set custom targeting parameters value can be String and Integer
                .setTargetingParam("CMP", "true")
                // optional, callback triggered when message choice is selected when called choice
                // type will be available as Integer at cLib.choiceType
                .setOnMessageChoiceSelect(new ConsentLib.Callback() {
                    @Override
                    public void run(ConsentLib c) {
                        Log.i(TAG, "Choice type selected by user: " + c.choiceType.toString());
                    }
                })
                // optional, callback triggered when consent data is captured when called
                .setOnInteractionComplete(new ConsentLib.Callback() {
                    @Override
                    public void run(ConsentLib c) {
                        Log.i(TAG, "euconsent prop: " + c.euconsent);
                        Log.i(TAG, "consentUUID prop: " + c.consentUUID);
                        Log.i(TAG, "euconsent in shared preferences: " + sharedPref.getString(ConsentLib.EU_CONSENT_KEY, null));
                        Log.i(TAG, "consentUUID in shared preferences: " + sharedPref.getString(ConsentLib.CONSENT_UUID_KEY, null));
                        Log.i(TAG, "IABConsent_SubjectToGDPR in shared preferences: " + sharedPref.getString(ConsentLib.IAB_CONSENT_SUBJECT_TO_GDPR, null));
                        Log.i(TAG, "IABConsent_ConsentString in shared preferences: " + sharedPref.getString(ConsentLib.IAB_CONSENT_CONSENT_STRING, null));
                        Log.i(TAG, "IABConsent_ParsedPurposeConsents in shared preferences: " + sharedPref.getString(ConsentLib.IAB_CONSENT_PARSED_PURPOSE_CONSENTS, null));
                        Log.i(TAG, "IABConsent_ParsedVendorConsents in shared preferences: " + sharedPref.getString(ConsentLib.IAB_CONSENT_PARSED_VENDOR_CONSENTS, null));

                        try {
                            // Get the consents for a collection of non-IAB vendors
                            c.getCustomVendorConsents(
                                    new String[]{"5bf7f5c5461e09743fe190b3", "5b2adb86173375159f804c77"},
                                    new ConsentLib.OnLoadComplete() {
                                        public void onSuccess(Object result) {
                                            HashSet<ConsentLib.CustomVendorConsent> consents = (HashSet) result;
                                            for (ConsentLib.CustomVendorConsent consent : consents) {
                                                if (consent.id.equals("5bf7f5c5461e09743fe190b3")) {
                                                    Log.i(TAG, "Consented to non-IAB vendor 1: "+consent.name);
                                                }
                                                if (consent.id.equals("5b2adb86173375159f804c77")) {
                                                    Log.i(TAG, "Consented to non-IAB vendor 2: "+consent.name);
                                                }
                                            }
                                        }
                                    });

                            // Example usage of getting all purpose consent results
                            c.getCustomPurposeConsents(new ConsentLib.OnLoadComplete() {
                                public void onSuccess(Object result) {
                                    HashSet<ConsentLib.CustomPurposeConsent> consents = (HashSet) result;
                                    for (ConsentLib.CustomPurposeConsent consent : consents) {
                                        Log.i(TAG, "Consented to purpose: " + consent.name);
                                    }
                                }
                            });

                            // Example usage of getting IAB vendor consent results for a list of vendors
                            boolean[] IABVendorConsents = c.getIABVendorConsents(new int[]{81, 82});
                            Log.i(TAG, String.format("Consented to IAB vendors: 81 -> %b, 82 -> %b",
                                IABVendorConsents[0],
                                IABVendorConsents[1]
                            ));

                            // Example usage of getting IAB purpose consent results for a list of purposes
                            boolean[] IABPurposeConsents = c.getIABPurposeConsents(new int[]{2, 3});
                            Log.i(TAG, String.format("Consented to IAB purposes: 2 -> %b, 3 -> %b",
                                IABPurposeConsents[0],
                                IABPurposeConsents[1]
                            ));

                        } catch (ConsentLibException e) {
                            e.printStackTrace();
                        }
                    }
                })
                // generate ConsentLib at this point modifying builder will not do anything
                .build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button button = findViewById(R.id.review_consents);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final Activity activity = this;

        try {
            // build the consent lib and run it on app start
            buildConsentLib(activity).run();

            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View _v) {
                    try{
                        // build the consent lib and run it on button click
                        buildConsentLib(activity).run();
                    }
                    catch (Exception e) { e.printStackTrace(); }
                }
            });
        } catch (ConsentLibException e) {
            e.printStackTrace();
        }
    }
}