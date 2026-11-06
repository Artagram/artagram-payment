package com.artagram.payment.billing;

import com.artagram.payment.common.error.ApiException;
import com.artagram.payment.suscripcion.dto.CheckoutResponse;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Integración real con Mercado Pago (Checkout Pro). La selección Fake-vs-real
 * la hace Spring vía {@code app.pago.proveedor} (env {@code PAGO_PROVEEDOR}):
 * la existencia del bean ES la selección.
 * <p>
 * El webhook se configura en el panel de Mercado Pago (Tus integraciones), no
 * con notification_url en la preferencia: la clave con la que se firma la
 * notificación sale de esa configuración.
 */
@Component
@ConditionalOnProperty(prefix = "app.pago", name = "proveedor", havingValue = "mercadopago")
public class MercadoPagoProvider implements ProveedorPago {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoProvider.class);
    private static final String TITULO = "Artagram PRO - 1 mes";

    private final PagoProperties properties;

    public MercadoPagoProvider(PagoProperties properties) {
        if (properties.getMercadopagoAccessToken() == null || properties.getMercadopagoAccessToken().isBlank()) {
            throw new IllegalStateException("PAGO_PROVEEDOR=mercadopago requiere MP_ACCESS_TOKEN");
        }
        this.properties = properties;
        MercadoPagoConfig.setAccessToken(properties.getMercadopagoAccessToken());
    }

    @Override
    public String getNombre() {
        return "MERCADOPAGO";
    }

    /**
     * El retorno sale de la configuración y no del cliente: una URL enviada
     * por el navegador convertiría el checkout en una redirección abierta.
     */
    @Override
    public CheckoutResponse crearPreferencia(UUID usuarioId, String plan, String retornoUrl) {
        String retorno = properties.getRetornoUrl();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(plan)
                .title(TITULO)
                .quantity(1)
                .currencyId(properties.getProMoneda())
                .unitPrice(properties.getProPrecio())
                .build();
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(retorno)
                .pending(retorno)
                .failure(retorno)
                .build();
        PreferenceRequest.PreferenceRequestBuilder request = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(backUrls)
                .externalReference(usuarioId.toString());
        // Mercado Pago rechaza auto_return con URLs que no son https (localhost).
        if (retorno.startsWith("https://")) {
            request.autoReturn("approved");
        }
        try {
            Preference preferencia = new PreferenceClient().create(request.build());
            return new CheckoutResponse(preferencia.getInitPoint(), preferencia.getId(), getNombre());
        } catch (MPApiException e) {
            log.error("Mercado Pago rechazó la preferencia: status={} body={}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            throw noDisponible();
        } catch (MPException e) {
            log.error("No se pudo crear la preferencia en Mercado Pago: {}", e.getMessage());
            throw noDisponible();
        }
    }

    @Override
    public Optional<PagoConsultado> consultarPago(String idPago) {
        try {
            Payment pago = new PaymentClient().get(Long.valueOf(idPago));
            return Optional.of(new PagoConsultado(String.valueOf(pago.getId()), pago.getStatus(),
                    pago.getExternalReference(), pago.getTransactionAmount(), pago.getCurrencyId()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        } catch (MPApiException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            log.error("Mercado Pago falló al consultar el pago {}: status={}", idPago, e.getStatusCode());
            throw noDisponible();
        } catch (MPException e) {
            log.error("No se pudo consultar el pago {}: {}", idPago, e.getMessage());
            throw noDisponible();
        }
    }

    private static ApiException noDisponible() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PROVEEDOR_PAGO_NO_DISPONIBLE",
                "No pudimos comunicarnos con Mercado Pago, intenta de nuevo");
    }
}
