package com.sh.plugins.capacitorbraintree;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.DropInListener;
import com.braintreepayments.api.DropInPaymentMethod;
import com.braintreepayments.api.GooglePayCardNonce;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.InvalidArgumentException;
import com.braintreepayments.api.PayPalAccountNonce;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.PostalAddress;
import com.braintreepayments.api.ThreeDSecureInfo;
import com.braintreepayments.api.ThreeDSecurePostalAddress;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.api.ThreeDSecureAdditionalInformation;
import com.braintreepayments.api.VenmoAccountNonce;
import com.braintreepayments.cardform.view.CardForm;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;

import org.json.JSONException;

@CapacitorPlugin(
        name = "Braintree"
)
public class BraintreePlugin extends Plugin implements DropInListener {
   private String clientToken;
   private BraintreeClientTokenProvider clientTokenProvider;

    /**
     * Logger tag
     */
    private static final String PLUGIN_TAG = "Braintree";

    static final String EXTRA_PAYMENT_RESULT = "payment_result";
    static final String EXTRA_DEVICE_DATA = "device_data";
    //static final String EXTRA_COLLECT_DEVICE_DATA = "collect_device_data";
    private String deviceData = "";

    private PluginCall showDropInCall;

    /**
     * In this version (simplified) using only "dropin" with nonce processed on server-side
     */
    static final int DROP_IN_REQUEST = 1;
    // private static final int GOOGLE_PAYMENT_REQUEST = 2;
    // private static final int CARDS_REQUEST = 3;
    // private static final int PAYPAL_REQUEST = 4;
    // private static final int VENMO_REQUEST = 5;
    // private static final int VISA_CHECKOUT_REQUEST = 6;
    // private static final int LOCAL_PAYMENTS_REQUEST = 7;
    // private static final int PREFERRED_PAYMENT_METHODS_REQUEST = 8;

    DropInClient dropInClient = null;

    @Override
    public void load() {
        Log.d(PLUGIN_TAG, "dropInClient, load...");
        clientTokenProvider = new BraintreeClientTokenProvider();
        BraintreePlugin plugin = this;
        Bridge bridge = this.getBridge();
        Activity activity = bridge.getActivity();
        activity.runOnUiThread(() -> {
            FragmentActivity factivity = null;
            if (activity instanceof FragmentActivity) {
                factivity = (FragmentActivity) activity;
                dropInClient = new DropInClient(factivity, clientTokenProvider);
                dropInClient.setListener(plugin);
            } else {
                Log.d(PLUGIN_TAG, "No fragment activity... ");
            }
        });
        Log.d(PLUGIN_TAG, "Plugin loaded");
    }

    /**
     * Called when the plugin is removed from the project.
     */
    @PluginMethod()
    public void getDeviceData(PluginCall call) {
        String merchantId = call.getString("merchantId");
        if (merchantId == null) {
            call.reject("A Merchant ID is required.");
            return;
        }
        try {
           JSObject deviceDataMap = new JSObject(this.deviceData);
            call.resolve(deviceDataMap);
        } catch (JSONException e) {
            call.reject("Cannot get device data");
        }
    }

    @PluginMethod()
    public void setToken(PluginCall call) throws InvalidArgumentException {
        String token = call.getString("token");
        clientTokenProvider.setClientToken(token);

        if (!call.getData().has("token")){
            call.reject("A token is required.");
            return;
        }
        this.clientToken = token;
        call.resolve();
    }

    @PluginMethod()
    public void getTickets(PluginCall call) {
        call.resolve();
    }

    @PluginMethod()
    public void getRecentMethods(PluginCall call) throws InvalidArgumentException {
        Log.d(PLUGIN_TAG, "getRecentMethods...");
        String token = call.getString("token");
        this.clientToken = token;
        clientTokenProvider.setClientToken(token);

        if (!call.getData().has("token")){
            call.reject("A token is required.");
            Log.d(PLUGIN_TAG, "A token is required...");
            return;
        }

        Bridge bridge = this.getBridge();
        Activity activity = bridge.getActivity();
        activity.runOnUiThread(() -> {
            dropInClient.fetchMostRecentPaymentMethod((FragmentActivity) activity, (dropInResult, error) -> {
                Log.d(PLUGIN_TAG, "fetchMostRecentPaymentMethod, callback...");
                if (error != null) {
                    JSObject resultMap = new JSObject();
                    resultMap.put("previousPayment", false);
                    call.resolve(resultMap);
                    Log.d(PLUGIN_TAG, "fetchMostRecentPaymentMethod, error, previousPayment...");
                    return;
                }

                if (dropInResult.getPaymentMethodType() != null) {
                    Log.d(PLUGIN_TAG, "fetchMostRecentPaymentMethod, getPaymentMethodType...");
                    // use the icon and name to show in your UI
                    int icon = dropInResult.getPaymentMethodType().getDrawable();
                    int name = dropInResult.getPaymentMethodType().getLocalizedName();

                    DropInPaymentMethod paymentMethodType = dropInResult.getPaymentMethodType();
                    if (paymentMethodType == DropInPaymentMethod.GOOGLE_PAY) {
                        // The last payment method the user used was Google Pay.
                        // The Google Pay flow will need to be performed by the
                        // user again at the time of checkout.
                        JSObject resultMap = new JSObject();
                        resultMap.put("previousPayment", false);
                        Log.d(PLUGIN_TAG, "fetchMostRecentPaymentMethod, GOOGLE_PAY...");
                        call.resolve(resultMap);
                    } else {
                        // Use the payment method show in your UI and charge the user
                        // at the time of checkout.
                        JSObject resultMap = new JSObject();
                        resultMap.put("previousPayment", true);
                        PaymentMethodNonce paymentMethod = dropInResult.getPaymentMethodNonce();
                        Log.d(PLUGIN_TAG, "getRecentMethods, handleNonce...");
                        resultMap.put("data", handleNonce(paymentMethod, dropInResult.getDeviceData()));
                        Log.d(PLUGIN_TAG, "fetchMostRecentPaymentMethod, getPaymentMethodType, else...");
                        call.resolve(resultMap);
                    }
                } else {
                    Log.d(PLUGIN_TAG, "fetchMostRecentPaymentMethod, else...");
                    // there was no existing payment method
                    JSObject resultMap = new JSObject();
                    resultMap.put("previousPayment", false);
                    call.resolve(resultMap);
                }
            });
        });

        call.resolve();
    }

    @PluginMethod()
    public void showDropIn(PluginCall call) {
        showDropInCall = call;
        // ThreeD settings
        ThreeDSecurePostalAddress address = new ThreeDSecurePostalAddress();
        String givenName = call.getString("givenName");
        Log.d(PLUGIN_TAG, "givenName: " + givenName);
        address.setGivenName(givenName); // ASCII-printable characters required, else will throw a validation error
        String surname = call.getString("surname");
        Log.d(PLUGIN_TAG, "surname: " + surname);
        address.setSurname(surname); // ASCII-printable characters required, else will throw a validation error
        address.setPhoneNumber(call.getString("phoneNumber"));
        address.setStreetAddress(call.getString("streetAddress"));
        address.setLocality(call.getString("locality"));
        address.setPostalCode(call.getString("postalCode"));
        address.setCountryCodeAlpha2(call.getString("countryCodeAlpha2"));

        ThreeDSecureAdditionalInformation additionalInformation = new ThreeDSecureAdditionalInformation();
        additionalInformation.setShippingAddress(address);

        ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
        threeDSecureRequest.setAmount(call.getString("amount"));
        threeDSecureRequest.setEmail(call.getString("email"));
        threeDSecureRequest.setBillingAddress(address);
        threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);
        threeDSecureRequest.setAdditionalInformation(additionalInformation);

        clientTokenProvider.setClientToken(clientToken);

        Bridge bridge = this.getBridge();
        Activity activity = bridge.getActivity();

        BraintreePlugin plugin = this;
        activity.runOnUiThread(() -> {
            DropInRequest dropInRequest = new DropInRequest();
            dropInRequest.setCardholderNameStatus(CardForm.FIELD_REQUIRED);
            //dropInRequest.requestThreeDSecureVerification(true); ///??? removed, what to do with it?
//            dropInRequest.collectDeviceData(true); ///??? removed, what to do with it?
            dropInRequest.setThreeDSecureRequest(threeDSecureRequest);
            dropInRequest.setVaultManagerEnabled(true);

            if (call.hasOption("deleteMethods")) {
                Log.d(PLUGIN_TAG, "dropInClient, has deleteMethods...");
                dropInRequest.setGooglePayDisabled(true);
                dropInRequest.setCardDisabled(true);
            }

            Log.d(PLUGIN_TAG, "dropInClient, GooglePayRequest...");
            GooglePayRequest googlePaymentRequest = new GooglePayRequest();
            googlePaymentRequest.setTransactionInfo(TransactionInfo.newBuilder()
                            .setTotalPrice(call.getString("amount"))
                            .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                            .setCurrencyCode(call.getString("currencyCode"))

                            .build());
            googlePaymentRequest.setBillingAddressRequired(true);
            //googlePaymentRequest.setGoogleMerchantId(call.getString("googleMerchantId")); ///??? deprecated
            dropInRequest.setGooglePayRequest(googlePaymentRequest);

            Log.d(PLUGIN_TAG, "showDropIn started...");
            dropInClient.launchDropIn(dropInRequest);
        });
    }

    /**
     *
     */
    private JSObject handleCanceled() {
        Log.d(PLUGIN_TAG, "handleCanceled...");
        JSObject resultMap = new JSObject();
        resultMap.put("cancelled", true);

        return resultMap;
    }

    private JSObject formatAddress(PostalAddress address) {
        JSObject addressMap = new JSObject();
        addressMap.put("name", address.getRecipientName());
        addressMap.put("address1", address.getStreetAddress());
        addressMap.put("address2", address.getExtendedAddress());
        addressMap.put("locality", address.getLocality());
        addressMap.put("administrativeArea", address.getRegion());
        addressMap.put("postalCode", address.getPostalCode());
        addressMap.put("countryCode", address.getCountryCodeAlpha2());
        return addressMap;
    }

    /**
     * Helper used to return a dictionary of values from the given payment method nonce.
     * Handles several different types of nonces (eg for cards, PayPal, etc).
     *
     * @param paymentMethodNonce The nonce used to build a dictionary of data from.
     * @param deviceData Device info
     */
    private JSObject handleNonce(PaymentMethodNonce paymentMethodNonce, String deviceData) {
        Log.d(PLUGIN_TAG, "handleNonce2..." + paymentMethodNonce.getString());

        JSObject resultMap = new JSObject();
        resultMap.put("cancelled", false);
        Log.d(PLUGIN_TAG, "handleNonce, paymentMethodNonce.getString...");
        resultMap.put("nonce", paymentMethodNonce.getString());
        resultMap.put("localizedDescription", paymentMethodNonce.describeContents());
        resultMap.put("type", paymentMethodNonce.getString());
        resultMap.put("localizedDescription", paymentMethodNonce.getString());
        this.deviceData = deviceData;
        Log.d(PLUGIN_TAG, "handleNonce, resultMap...");
        resultMap.put("deviceData", deviceData);

        // Card
        Log.d(PLUGIN_TAG, "handleNonce, Card...");
        if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce)paymentMethodNonce;
            resultMap.put("type", cardNonce.getCardType());
            resultMap.put("localizedDescription", paymentMethodNonce.describeContents());

            JSObject innerMap = new JSObject();
            innerMap.put("lastTwo", cardNonce.getLastTwo());
            innerMap.put("network", cardNonce.getCardType());
            innerMap.put("type", cardNonce.getCardType());
            innerMap.put("cardHolderName", cardNonce.getCardholderName());
            innerMap.put("token", cardNonce.toString());

            ThreeDSecureInfo threeDSecureInfo = cardNonce.getThreeDSecureInfo();
            if (threeDSecureInfo != null) {
                JSObject threeDMap = new JSObject();
                threeDMap.put("threeDSecureVerified", threeDSecureInfo.wasVerified());
                threeDMap.put("liabilityShifted", threeDSecureInfo.isLiabilityShifted());
                threeDMap.put("liabilityShiftPossible", threeDSecureInfo.isLiabilityShiftPossible());

                innerMap.put("threeDSecureCard", threeDMap);
            }
            if (resultMap.getString("localizedDescription").equals("Android Pay")) {
                resultMap.put("googlePay", innerMap);
            } else {
                resultMap.put("card", innerMap);
            }
        }

        // PayPal
        Log.d(PLUGIN_TAG, "handleNonce, PayPal...");
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce payPalAccountNonce = (PayPalAccountNonce)paymentMethodNonce;
            JSObject innerMap = new JSObject();
            resultMap.put("email", payPalAccountNonce.getEmail());
            resultMap.put("firstName", payPalAccountNonce.getFirstName());
            resultMap.put("lastName", payPalAccountNonce.getLastName());
            resultMap.put("phone", payPalAccountNonce.getPhone());
            resultMap.put("localizedDescription", paymentMethodNonce.describeContents());
            // resultMap.put("billingAddress", payPalAccountNonce.getBillingAddress()); //TODO
            // resultMap.put("shippingAddress", payPalAccountNonce.getShippingAddress()); //TODO
            resultMap.put("clientMetadataId", payPalAccountNonce.getClientMetadataId());
            resultMap.put("payerId", payPalAccountNonce.getPayerId());
            resultMap.put("payPalAccount", innerMap);
        }

        // 3D Secure
        if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
        }

        // Venmo
        if (paymentMethodNonce instanceof VenmoAccountNonce) {
            VenmoAccountNonce venmoAccountNonce = (VenmoAccountNonce) paymentMethodNonce;
            resultMap.put("localizedDescription", venmoAccountNonce.describeContents());

            JSObject innerMap = new JSObject();
            innerMap.put("username", venmoAccountNonce.getUsername());
            resultMap.put("venmoAccount", innerMap);
        }

        Log.d(PLUGIN_TAG, "handleNonce, GooglePay...");
        if (paymentMethodNonce instanceof GooglePayCardNonce) {
            GooglePayCardNonce googlePayCardNonce = (GooglePayCardNonce) paymentMethodNonce;
            resultMap.put("type", googlePayCardNonce.getCardType());
            resultMap.put("localizedDescription", googlePayCardNonce.describeContents());
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap...");

            JSObject innerMap = new JSObject();
            innerMap.put("lastTwo", googlePayCardNonce.getLastTwo());
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap1...");
            innerMap.put("email", googlePayCardNonce.getEmail());
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap2...");
            innerMap.put("network", googlePayCardNonce.getCardNetwork());
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap3...");
            innerMap.put("type", googlePayCardNonce.getCardType());
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap4...");
            innerMap.put("token", googlePayCardNonce.getString());
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap5...");
            innerMap.put("billingAddress", formatAddress(googlePayCardNonce.getBillingAddress()));
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap6...");
            innerMap.put("shippingAddress", formatAddress(googlePayCardNonce.getShippingAddress()));
            Log.d(PLUGIN_TAG, "handleNonce, GooglePay, innermap7...");
            resultMap.put("googlePay", innerMap);
        }

        Log.d(PLUGIN_TAG, "handleNonce, resultMap: " + resultMap.toString());
        return resultMap;
    }

    @Override
    public void onDropInSuccess(@NonNull DropInResult dropInResult) {
          Log.d(PLUGIN_TAG, "onDropInSuccess..." + dropInResult.getPaymentMethodNonce().getString());
          PaymentMethodNonce paymentMethodNonce = dropInResult.getPaymentMethodNonce();
          String deviceData = dropInResult.getDeviceData();
          showDropInCall.resolve(handleNonce(paymentMethodNonce, deviceData));
          Log.d(PLUGIN_TAG, "onDropInSuccess, handleNonce...sent");
    }

    @Override
    public void onDropInFailure(@NonNull Exception error) {
        Log.d(PLUGIN_TAG, "onDropInFailure..." + (error != null ? error.getMessage() : "no error"));
        showDropInCall.resolve(handleCanceled());
    }
}
