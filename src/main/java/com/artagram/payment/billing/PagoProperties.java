package com.artagram.payment.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pago")
public class PagoProperties {

    private String proveedor = "fake";
    private String mercadopagoAccessToken = "";
    private String mercadopagoWebhookSecret = "";

    public String getProveedor() {
        return proveedor;
    }

    public void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public String getMercadopagoAccessToken() {
        return mercadopagoAccessToken;
    }

    public void setMercadopagoAccessToken(String mercadopagoAccessToken) {
        this.mercadopagoAccessToken = mercadopagoAccessToken;
    }

    public String getMercadopagoWebhookSecret() {
        return mercadopagoWebhookSecret;
    }

    public void setMercadopagoWebhookSecret(String mercadopagoWebhookSecret) {
        this.mercadopagoWebhookSecret = mercadopagoWebhookSecret;
    }
}
