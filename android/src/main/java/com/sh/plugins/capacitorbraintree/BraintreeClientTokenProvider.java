package com.sh.plugins.capacitorbraintree;

import androidx.annotation.NonNull;

import com.braintreepayments.api.ClientTokenCallback;
import com.braintreepayments.api.ClientTokenProvider;

public class BraintreeClientTokenProvider implements ClientTokenProvider {
    String clientToken = null;

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    @Override
    public void getClientToken(@NonNull ClientTokenCallback clientTokenCallback) {
        if (clientToken != null) {
            // If the token is successfully fetched, invoke the callback with it
            clientTokenCallback.onSuccess(clientToken);
        } else {
            // If there's an error or token fetch fails, invoke the callback with an error

//            clientTokenCallback.onFailure("Failed to fetch client token");
            clientTokenCallback.onFailure(new Exception("Failed to fetch client token"));
        }

    }
}
