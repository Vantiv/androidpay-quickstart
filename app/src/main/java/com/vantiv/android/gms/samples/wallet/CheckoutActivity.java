/*
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vantiv.android.gms.samples.wallet;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.CardRequirements;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.fragment.SupportWalletFragment;
import com.google.android.gms.wallet.fragment.WalletFragmentInitParams;
import com.google.android.gms.wallet.fragment.WalletFragmentMode;
import com.google.android.gms.wallet.fragment.WalletFragmentOptions;
import com.google.android.gms.wallet.fragment.WalletFragmentStyle;

import java.util.Arrays;

/**
 * The checkout page.
 *
 * Handles login and logout, but most of the interesting things happen in the WalletFragment
 * that is hosted by this activity.
 * Other pages further in the checkout process will send users back to this page if an error occurs,
 * so {@link #onNewIntent(Intent)} needs to check to see if an error code has been passed in.
 */
public class CheckoutActivity extends BikestoreFragmentActivity implements
        View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "CheckoutActivity";
    private static final int REQUEST_CODE_MASKED_WALLET = 1001;

    private SupportWalletFragment mWalletFragment;
    private int mItemId;
    private Button mReturnToShopping;
    private Button mContinueCheckout;
    private CheckBox mStripeCheckbox;
    private CheckBox mVantivCheckbox;
    private boolean mUseStripe = false;
    private boolean mUseVantiv = false;
    private GoogleApiClient mGoogleApiClient;
    private PaymentsClient mPaymentsClient;
    private ProgressDialog mProgressDialog;
    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 1313;
    private final Activity activity = (Activity) this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        // [START basic_google_api_client]
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                        .setEnvironment(Constants.WALLET_ENVIRONMENT)
                        .build())
                .enableAutoManage(this, this)
                .build();
        // [END basic_google_api_client]

        mPaymentsClient =
                Wallet.getPaymentsClient(
                        this,
                        new Wallet.WalletOptions.Builder()
                                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                                .build());

        isReadyToPay();

        mItemId = getIntent().getIntExtra(Constants.EXTRA_ITEM_ID, 0);
        mReturnToShopping = (Button) findViewById(R.id.button_return_to_shopping);
        mReturnToShopping.setOnClickListener(this);
        mContinueCheckout = (Button) findViewById(R.id.button_regular_checkout);
        mContinueCheckout.setOnClickListener(this);

        mStripeCheckbox = (CheckBox) findViewById(R.id.checkbox_stripe);
        mStripeCheckbox.setOnCheckedChangeListener(this);

        mVantivCheckbox = (CheckBox) findViewById(R.id.checkbox_vantiv);
        mVantivCheckbox.setOnCheckedChangeListener(this);

        // Check if user is ready to use Android Pay
        // [START is_ready_to_pay]
        showProgressDialog();
        Wallet.Payments.isReadyToPay(mGoogleApiClient).setResultCallback(
                new ResultCallback<BooleanResult>() {
                    @Override
                    public void onResult(@NonNull BooleanResult booleanResult) {
                        hideProgressDialog();

                        if (booleanResult.getStatus().isSuccess()) {
                            if (booleanResult.getValue()) {
                                // Show Android Pay buttons and hide regular checkout button
                                // [START_EXCLUDE]
                                Log.d(TAG, "isReadyToPay:true");
                                createAndAddWalletFragment();
                                findViewById(R.id.button_regular_checkout)
                                        .setVisibility(View.GONE);
                                // [END_EXCLUDE]
                            } else {
                                // Hide Android Pay buttons, show a message that Android Pay
                                // cannot be used yet, and display a traditional checkout button
                                // [START_EXCLUDE]
                                Log.d(TAG, "isReadyToPay:false:" + booleanResult.getStatus());
                                findViewById(R.id.layout_android_pay_checkout)
                                        .setVisibility(View.GONE);
                                findViewById(R.id.android_pay_message)
                                        .setVisibility(View.VISIBLE);
                                findViewById(R.id.button_regular_checkout)
                                        .setVisibility(View.VISIBLE);
                                // [END_EXCLUDE]
                            }
                        } else {
                            // Error making isReadyToPay call
                            Log.e(TAG, "isReadyToPay:" + booleanResult.getStatus());
                        }
                    }
                });
        // [END is_ready_to_pay]

        try {
            findViewById(R.id.button_pay_with_google)
                    .setOnClickListener(this);
        }
        catch (Exception e) {
            // This will catch any exception, because they are all descended from Exception
            System.out.println("Error " + e.getMessage());
        }
    }

    private void isReadyToPay() {
        IsReadyToPayRequest request =
                IsReadyToPayRequest.newBuilder()
                        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
                        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
                        .build();
        Task<Boolean> task = mPaymentsClient.isReadyToPay(request);
        task.addOnCompleteListener(
                new OnCompleteListener<Boolean>() {
                    public void onComplete(Task<Boolean> task) {
                        try {
                            boolean result = task.getResult(ApiException.class);
                            if (result == true) {
                                // Show Google as payment option.
                            } else {
                                // Hide Google as payment option.
                            }
                        } catch (ApiException exception) {
                        }
                    }
                });
    }

    private PaymentDataRequest createPaymentDataRequest() {
        PaymentDataRequest.Builder request =
                PaymentDataRequest.newBuilder()
                        .setTransactionInfo(
                                TransactionInfo.newBuilder()
                                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                                        .setTotalPrice("10.00")
                                        .setCurrencyCode("USD")
                                        .build())
                        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
                        .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
                        .setCardRequirements(
                                CardRequirements.newBuilder()
                                        .addAllowedCardNetworks(
                                                Arrays.asList(
                                                        WalletConstants.CARD_NETWORK_AMEX,
                                                        WalletConstants.CARD_NETWORK_DISCOVER,
                                                        WalletConstants.CARD_NETWORK_VISA,
                                                        WalletConstants.CARD_NETWORK_MASTERCARD))
                                        .build());

        PaymentMethodTokenizationParameters params =
                PaymentMethodTokenizationParameters.newBuilder()
                        .setPaymentMethodTokenizationType(
                                WalletConstants.PAYMENT_METHOD_TOKENIZATION_TYPE_PAYMENT_GATEWAY)
                        .addParameter("gateway", "vantiv")
                        //.addParameter("gatewayMerchantId", "yourMerchantIdGivenFromYourGateway")
                        .addParameter("vantiv:merchantPayPageId", getString(R.string.vantiv_paypageid))
                        .addParameter("vantiv:merchantOrderId", "orderId")
                        .addParameter("vantiv:merchantTransactionId", "tranId")
                        .addParameter("vantiv:merchantReportGroup", "reportGroup")
                        .build();

        request.setPaymentMethodTokenizationParameters(params);
        return request.build();
    }

    // [START on_activity_result]
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // retrieve the error code, if available
        int errorCode = -1;
        if (data != null) {
            errorCode = data.getIntExtra(WalletConstants.EXTRA_ERROR_CODE, -1);
        }
        switch (requestCode) {
            case REQUEST_CODE_MASKED_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        if (data != null) {
                            MaskedWallet maskedWallet =
                                    data.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                            launchConfirmationPage(maskedWallet);
                        }
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                    default:
                        handleError(errorCode);
                        break;
                }
                break;
            case WalletConstants.RESULT_ERROR:
                handleError(errorCode);
                break;
            case LOAD_PAYMENT_DATA_REQUEST_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        PaymentData paymentData = PaymentData.getFromIntent(data);
                        String token = paymentData.getPaymentMethodToken().getToken();
                          break;
                    case Activity.RESULT_CANCELED:
                        break;
                    case AutoResolveHelper.RESULT_ERROR:
                        Status status = AutoResolveHelper.getStatusFromIntent(data);
                        // Log the status for debugging.
                        // Generally, there is no need to show an error to
                        // the user as the Google Payment API will do that.
                        break;
                    default:
                        // Do nothing.
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
    // [END on_activity_result]

    /**
     * If the confirmation page encounters an error it can't handle, it will send the customer back
     * to this page.  The intent should include the error code as an {@code int} in the field
     * {@link WalletConstants#EXTRA_ERROR_CODE}.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.hasExtra(WalletConstants.EXTRA_ERROR_CODE)) {
            int errorCode = intent.getIntExtra(WalletConstants.EXTRA_ERROR_CODE, 0);
            handleError(errorCode);
        }
    }

    private void goToItemListActivity() {
        Intent intent = new Intent(this, ItemListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void continueCheckout() {
        Toast.makeText(this, R.string.checkout_bikestore_message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onClick(View v) {
        if (v == mReturnToShopping) {
            goToItemListActivity();
        } else if (v == mContinueCheckout) {
            continueCheckout();
        }
        else if (v == findViewById(R.id.button_pay_with_google)){
            PaymentDataRequest request = createPaymentDataRequest();
            if (request != null) {
                AutoResolveHelper.resolveTask(
                        mPaymentsClient.loadPaymentData(request),
                        this,
                        // LOAD_PAYMENT_DATA_REQUEST_CODE is a constant value
                        // you define.
                        LOAD_PAYMENT_DATA_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.checkbox_stripe) {
            mUseStripe = isChecked;

            // Re-create the buy-button with the proper processor
            createAndAddWalletFragment();
        }
        if(buttonView.getId() == R.id.checkbox_vantiv) {
            mUseVantiv = isChecked;

            // Re-create the buy-button with the proper processor
            createAndAddWalletFragment();
        }
    }

    private void createAndAddWalletFragment() {
        // [START fragment_style_and_options]
        WalletFragmentStyle walletFragmentStyle = new WalletFragmentStyle()
                .setBuyButtonText(WalletFragmentStyle.BuyButtonText.BUY_WITH)
                .setBuyButtonAppearance(WalletFragmentStyle.BuyButtonAppearance.ANDROID_PAY_DARK)
                .setBuyButtonWidth(WalletFragmentStyle.Dimension.MATCH_PARENT);

        WalletFragmentOptions walletFragmentOptions = WalletFragmentOptions.newBuilder()
                .setEnvironment(Constants.WALLET_ENVIRONMENT)
                .setFragmentStyle(walletFragmentStyle)
                .setTheme(WalletConstants.THEME_LIGHT)
                .setMode(WalletFragmentMode.BUY_BUTTON)
                .build();
        mWalletFragment = SupportWalletFragment.newInstance(walletFragmentOptions);
        // [END fragment_style_and_options]

        // Now initialize the Wallet Fragment
        String accountName = ((BikestoreApplication) getApplication()).getAccountName();
        MaskedWalletRequest maskedWalletRequest;
        if (mUseStripe) {
            // Stripe integration
            maskedWalletRequest = WalletUtil.createStripeMaskedWalletRequest(
                    Constants.ITEMS_FOR_SALE[mItemId],
                    getString(R.string.stripe_publishable_key),
                    getString(R.string.stripe_version));
        }
        else if (mUseVantiv) {
            // Vantiv integration
            maskedWalletRequest = WalletUtil.createVantivMaskedWalletRequest(
                    Constants.ITEMS_FOR_SALE[mItemId],
                    getString(R.string.vantiv_paypageid),
                    "orderId",
                    "tranId",
                    "reportGroup");
        } else {
            // Direct integration
            maskedWalletRequest = WalletUtil.createMaskedWalletRequest(
                    Constants.ITEMS_FOR_SALE[mItemId],
                    getString(R.string.public_key));
        }

        // [START params_builder]
        WalletFragmentInitParams.Builder startParamsBuilder = WalletFragmentInitParams.newBuilder()
                .setMaskedWalletRequest(maskedWalletRequest)
                .setMaskedWalletRequestCode(REQUEST_CODE_MASKED_WALLET)
                .setAccountName(accountName);
        mWalletFragment.initialize(startParamsBuilder.build());

        // add Wallet fragment to the UI
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.dynamic_wallet_button_fragment, mWalletFragment)
                .commit();
        // [END params_builder]
    }

    private void launchConfirmationPage(MaskedWallet maskedWallet) {
        Intent intent = new Intent(this, ConfirmationActivity.class);
        intent.putExtra(Constants.EXTRA_ITEM_ID, mItemId);
        intent.putExtra(Constants.EXTRA_MASKED_WALLET, maskedWallet);
        startActivity(intent);
    }

    @Override
    public Fragment getResultTargetFragment() {
        return null;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed:" + connectionResult.getErrorMessage());
        Toast.makeText(this, "Google Play Services error", Toast.LENGTH_SHORT).show();
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage("Loading...");
        }

        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.hide();
        }
    }
}
