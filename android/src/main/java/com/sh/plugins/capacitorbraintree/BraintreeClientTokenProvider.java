package com.sh.plugins.capacitorbraintree;

import androidx.annotation.NonNull;

import com.braintreepayments.api.ClientTokenCallback;
import com.braintreepayments.api.ClientTokenProvider;
/**
 * A {@link ClientTokenProvider} implementation that returns a client token that is set by the
 * developer.
 */
public class BraintreeClientTokenProvider implements ClientTokenProvider {
    String clientToken = null;
    /**
     * Sets a client token to use for requests.
     *
     * @param clientToken The client token to use for requests.
     */
    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }
    /**
     * Fetches a client token from your server.
     *
     * @param clientTokenCallback {@link ClientTokenCallback} to receive the client token or an error.
     */
    @Override
    public void getClientToken(@NonNull ClientTokenCallback clientTokenCallback) {
        if (clientToken != null) {
            clientTokenCallback.onSuccess(clientToken);
        } else {
            clientTokenCallback.onFailure(new Exception("Failed to fetch client token"));
        }

    }
}
