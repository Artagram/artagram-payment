package com.artagram.payment.billing;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pago")
public class PagoProperties {

    private String proveedor = "fake";
    private String mercadopagoAccessToken = "";
    private String mercadopagoWebhookSecret = "";
    private BigDecimal proPrecio = new BigDecimal("20000");
    private String proMoneda = "COP";
    private String retornoUrl = "http://localhost:5173/suscripcion/resultado";

    public BigDecimal getProPrecio() {
        return proPrecio;
    }

    public void setProPrecio(BigDecimal proPrecio) {
        this.proPrecio = proPrecio;
    }

    public String getProMoneda() {
        return proMoneda;
    }

    public void setProMoneda(String proMoneda) {
        this.proMoneda = proMoneda;
    }

    public String getRetornoUrl() {
        return retornoUrl;
    }

    public void setRetornoUrl(String retornoUrl) {
        this.retornoUrl = retornoUrl;
    }

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
