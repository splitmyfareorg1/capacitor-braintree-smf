package com.sh.plugins.capacitorbraintree;

import android.app.ProgressDialog;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.DropInListener;
import com.braintreepayments.api.DropInPaymentMethod;
import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.GooglePayCardNonce;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalAccountNonce;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.PostalAddress;
import com.braintreepayments.api.ThreeDSecureAdditionalInformation;
import com.braintreepayments.api.ThreeDSecureClient;
import com.braintreepayments.api.ThreeDSecureInfo;
import com.braintreepayments.api.ThreeDSecureListener;
import com.braintreepayments.api.ThreeDSecurePostalAddress;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.api.ThreeDSecureResult;
import com.braintreepayments.api.UserCanceledException;
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

import org.json.JSONException;

@CapacitorPlugin(
        name = "Braintree"
)
public class BraintreePlugin extends Plugin implements DropInListener, ThreeDSecureListener {

   private BraintreeClientTokenProvider clientTokenProvider;

    /**
     * Logger tag
     */
    private static final String PLUGIN_TAG = "Braintree";

    static final String EXTRA_PAYMENT_RESULT = "payment_result";
    static final String EXTRA_DEVICE_DATA = "device_data";
    private String deviceData = "";

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

    DropInClient dropInClient;
    ThreeDSecureClient threeDSecureClient;
    private PluginCall pluginCall;
    private DropInResult tmpDropinResult;

    @Override
    public void load() {
        clientTokenProvider = new BraintreeClientTokenProvider();
        Bridge bridge = this.getBridge();
        FragmentActivity activity = bridge.getActivity();
        activity.runOnUiThread(() -> {
            dropInClient = new DropInClient(activity, clientTokenProvider);
            dropInClient.setListener(this);
            BraintreeClient braintreeClient = new BraintreeClient(this.getContext(), clientTokenProvider);
            threeDSecureClient = new ThreeDSecureClient(activity, braintreeClient);
            threeDSecureClient.setListener(this);
        });
    }

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
    public void setToken(PluginCall call) throws IllegalArgumentException {
        String token = call.getString("token");

        if (!call.getData().has("token")){
            call.reject("A token is required.");
            return;
        }
        this.clientTokenProvider.setClientToken(token);
        call.resolve();
    }

    @PluginMethod()
    public void getTickets(PluginCall call) {
        call.resolve();
    }

    @PluginMethod()
    public void getRecentMethods(PluginCall call) throws IllegalArgumentException {
        String token = call.getString("token");
        this.clientTokenProvider.setClientToken(token);

        if (!call.getData().has("token")){
            call.reject("A token is required.");
            return;
        }

        Bridge bridge = this.getBridge();
        FragmentActivity activity = bridge.getActivity();
        activity.runOnUiThread(() -> {
            dropInClient.fetchMostRecentPaymentMethod(activity, (result, e) -> {
                if (e != null) {
                    JSObject resultMap = new JSObject();
                    resultMap.put("previousPayment", false);
                    call.resolve(resultMap);
                    return;
                }

                if (result.getPaymentMethodType() != null) {
                    // use the icon and name to show in your UI
                    int icon = result.getPaymentMethodType().getDrawable();
                    int name = result.getPaymentMethodType().getLocalizedName();

                    DropInPaymentMethod paymentMethodType = result.getPaymentMethodType();
                    if (paymentMethodType == DropInPaymentMethod.GOOGLE_PAY) {
                        // The last payment method the user used was Google Pay.
                        // The Google Pay flow will need to be performed by the
                        // user again at the time of checkout.
                        JSObject resultMap = new JSObject();
                        resultMap.put("previousPayment", false);
                        call.resolve(resultMap);
                    } else {
                        // Use the payment method show in your UI and charge the user
                        // at the time of checkout.
                        JSObject resultMap = new JSObject();
                        resultMap.put("previousPayment", true);
                        PaymentMethodNonce paymentMethod = result.getPaymentMethodNonce();
                        resultMap.put("data", handleNonce(result, null));
                        call.resolve(resultMap);
                    }
                } else {
                    // there was no existing payment method
                    JSObject resultMap = new JSObject();
                    resultMap.put("previousPayment", false);
                    call.resolve(resultMap);
                }
            });
        });


    }

    @PluginMethod()
    public void showDropIn(PluginCall call) {


        ThreeDSecureRequest threeDSecureRequest = this.create3DSRequest(call);

        DropInRequest dropInRequest = new DropInRequest(false);
        dropInRequest.setCardholderNameStatus(CardForm.FIELD_REQUIRED);
        dropInRequest.setThreeDSecureRequest(threeDSecureRequest);
        dropInRequest.setVaultManagerEnabled(true);

        if (call.hasOption("deleteMethods")) {
            dropInRequest.setGooglePayDisabled(true);
            dropInRequest.setCardDisabled(true);
        }

        GooglePayRequest googlePaymentRequest = new GooglePayRequest();
        googlePaymentRequest.setTransactionInfo(TransactionInfo.newBuilder()
                .setTotalPrice(call.getString("amount"))
                .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                .setCurrencyCode(call.getString("currencyCode"))
                .build());
        googlePaymentRequest.setBillingAddressRequired(true);
        googlePaymentRequest.setGoogleMerchantName(call.getString("googleMerchantId"));
        dropInRequest.setGooglePayRequest(googlePaymentRequest);
        Bridge bridge = this.getBridge();
        FragmentActivity activity = bridge.getActivity();
        activity.runOnUiThread(() -> {
            dropInClient.setListener(this);
            this.pluginCall = call;
            dropInClient.launchDropIn(dropInRequest);

            Log.d(PLUGIN_TAG, "showDropIn started");
        });
    }

    private ThreeDSecureRequest create3DSRequest(PluginCall call) {
        ThreeDSecurePostalAddress address = new ThreeDSecurePostalAddress();
        address.setGivenName(call.getString("givenName"));
        address.setSurname(call.getString("surname"));
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

        return threeDSecureRequest;
    }
    private JSObject handleCanceled() {
        Log.d(PLUGIN_TAG, "handleNonce");

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
     * @param dropInResult The dropin result used to build a dictionary of data from.
     * @param deviceData Device info
     */
    private JSObject handleNonce(DropInResult dropInResult, @Nullable CardNonce threeDSCardNonce) {
        Log.d(PLUGIN_TAG, "handleNonce");
        String deviceData = dropInResult.getDeviceData();
        PaymentMethodNonce paymentMethodNonce = threeDSCardNonce == null ? dropInResult.getPaymentMethodNonce() : threeDSCardNonce;
        JSObject resultMap = new JSObject();
        resultMap.put("cancelled", false);
        resultMap.put("nonce", paymentMethodNonce.getString());
        resultMap.put("localizedDescription", dropInResult.getPaymentMethodType());
        this.deviceData = deviceData;
        resultMap.put("deviceData", deviceData);

        // Card
        if (threeDSCardNonce == null && paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce)paymentMethodNonce;

            JSObject innerMap = new JSObject();
            innerMap.put("lastTwo", cardNonce.getLastTwo());
            innerMap.put("network", cardNonce.getCardType());
            innerMap.put("cardHolderName", cardNonce.getCardholderName());
            innerMap.put("type", cardNonce.getCardType());
            innerMap.put("token", cardNonce.toString());


            ThreeDSecureInfo threeDSecureInfo = cardNonce.getThreeDSecureInfo();

            JSObject threeDMap = new JSObject();
            threeDMap.put("threeDSecureVerified", threeDSecureInfo.wasVerified());
            threeDMap.put("liabilityShifted", threeDSecureInfo.isLiabilityShifted());
            threeDMap.put("liabilityShiftPossible", threeDSecureInfo.isLiabilityShiftPossible());

            innerMap.put("threeDSecureCard", threeDMap);
            resultMap.put("card", innerMap);
        }

        // PayPal
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce payPalAccountNonce = (PayPalAccountNonce)paymentMethodNonce;

            JSObject innerMap = new JSObject();
            resultMap.put("email", payPalAccountNonce.getEmail());
            resultMap.put("firstName", payPalAccountNonce.getFirstName());
            resultMap.put("lastName", payPalAccountNonce.getLastName());
            resultMap.put("phone", payPalAccountNonce.getPhone());
            // resultMap.put("billingAddress", payPalAccountNonce.getBillingAddress()); //TODO
            // resultMap.put("shippingAddress", payPalAccountNonce.getShippingAddress()); //TODO
            resultMap.put("clientMetadataId", payPalAccountNonce.getClientMetadataId());
            resultMap.put("payerId", payPalAccountNonce.getPayerId());

            resultMap.put("payPalAccount", innerMap);
        }

        // Venmo
        if (paymentMethodNonce instanceof VenmoAccountNonce) {
            VenmoAccountNonce venmoAccountNonce = (VenmoAccountNonce) paymentMethodNonce;

            JSObject innerMap = new JSObject();
            innerMap.put("username", venmoAccountNonce.getUsername());

            resultMap.put("venmoAccount", innerMap);
        }

        if (paymentMethodNonce instanceof GooglePayCardNonce || (threeDSCardNonce != null)) {
            GooglePayCardNonce googlePayCardNonce = (GooglePayCardNonce) dropInResult.getPaymentMethodNonce();

            JSObject innerMap = new JSObject();
            innerMap.put("lastTwo", googlePayCardNonce.getLastTwo());
            innerMap.put("email", googlePayCardNonce.getEmail());
            innerMap.put("network", googlePayCardNonce.getCardType());
            innerMap.put("type", googlePayCardNonce.getCardType());
            innerMap.put("token", threeDSCardNonce != null ? threeDSCardNonce.toString() : null);
            innerMap.put("billingAddress", formatAddress(googlePayCardNonce.getBillingAddress()));
            innerMap.put("shippingAddress", formatAddress(googlePayCardNonce.getShippingAddress()));

            resultMap.put("googlePay", innerMap);
            resultMap.put("localizedDescription", "Android Pay");
        }

        return resultMap;
    }

    @Override
    public void onDropInSuccess(@NonNull DropInResult dropInResult) {

        Log.d(PLUGIN_TAG, "onDropInSuccess. dropInResult: " + dropInResult.toString());

        if (this.pluginCall == null) {
            return;
        }

        PaymentMethodNonce paymentMethodNonce = dropInResult.getPaymentMethodNonce();

        if (paymentMethodNonce instanceof GooglePayCardNonce) {
            GooglePayCardNonce nonce = (GooglePayCardNonce) paymentMethodNonce;
            if (nonce.isNetworkTokenized()) {
                this.pluginCall.resolve(handleNonce(dropInResult, null));
            } else {
                ThreeDSecureRequest threeDSecureRequest = this.create3DSRequest(this.pluginCall);
                threeDSecureRequest.setNonce(nonce.getString());
                ProgressDialog progress = new ProgressDialog(this.getContext());
                progress.setTitle("Loading");
                progress.setMessage("Performing 3DS check...");
                progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
                progress.show();

                this.threeDSecureClient.performVerification(this.getActivity(), threeDSecureRequest, (ThreeDSecureResult threeDSecureLookupResult, Exception lookupError) -> {
                    progress.dismiss();
                    if (lookupError != null) {
                        this.pluginCall.reject(lookupError.getMessage(), lookupError);
                        return;
                    }

                    if (threeDSecureLookupResult == null) {
                        this.pluginCall.reject("3DS lookup failed");
                        return;
                    }

                    this.tmpDropinResult = dropInResult;
                    threeDSecureClient.continuePerformVerification(this.getActivity(), threeDSecureRequest, threeDSecureLookupResult);

                });
            }
        } else {
            this.pluginCall.resolve(handleNonce(dropInResult, null));
        }
    }

    @Override
    public void onDropInFailure(@NonNull Exception e) {
        if (this.pluginCall == null) {
            return;
        }
        boolean isUserCanceled = (e instanceof UserCanceledException);
        if (isUserCanceled) {
            this.pluginCall.resolve(handleCanceled());
        } else {
            Log.e(PLUGIN_TAG, "Error: " + e.getMessage());
            this.pluginCall.reject(e.getMessage(), e);
        }
    }

    @Override
    public void onThreeDSecureSuccess(@NonNull ThreeDSecureResult threeDSecureResult) {
        CardNonce nonce = threeDSecureResult.getTokenizedCard();

        if (nonce == null) {
            this.pluginCall.reject("3DS verification failed");
        } else {
            this.pluginCall.resolve(this.handleNonce(this.tmpDropinResult, nonce));
        }
        this.tmpDropinResult = null;
    }

    @Override
    public void onThreeDSecureFailure(@NonNull Exception e) {
        this.tmpDropinResult = null;
        this.pluginCall.reject(e.getMessage(), e);
    }
}
