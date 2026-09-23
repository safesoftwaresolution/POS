package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider;
import com.sciencebot.pos.billing.internal.entities.FactusCredentials;
import com.sciencebot.pos.billing.internal.entities.NumberingRangeAudit;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeAuditRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de administración de Rangos de Numeración.
 *
 * <p>Además de delegar en el proveedor activo (Factus, mock...), aplica reglas propias del
 * sistema que protegen la numeración legal de un local:
 * <ul>
 *   <li>no se puede desactivar ni eliminar el rango que el local tiene configurado como su
 *       rango de numeración activo (primero hay que elegir otro);</li>
 *   <li>no se puede activar un rango si el local ya tiene otro rango activo para el mismo tipo
 *       de documento;</li>
 *   <li>el consecutivo actual no puede retroceder respecto al valor que reporta el proveedor.</li>
 * </ul>
 * El administrador del local elige cuál de sus rangos es el activo para su entorno
 * ({@link #setDefaultNumberingRange(Long)}); si registra un rango sin tener ninguno configurado,
 * ese rango queda como activo automáticamente.
 * Cada cambio (creación, eliminación, activación/desactivación, cambio de consecutivo, rango activo) queda
 * en {@code numbering_range_audit} con quién y cuándo lo hizo. Estas reglas dependen del local en
 * contexto ({@link TenantContext}); si no hay uno (por ejemplo, un test que llama al servicio
 * directamente) se aplican solo las reglas que no lo requieren (el consecutivo nunca retrocede).
 */
@Service
public class NumberingRangeServiceImpl implements NumberingRangeService {

    private static final Logger log = LoggerFactory.getLogger(NumberingRangeServiceImpl.class);

    private final Map<String, NumberingRangeProvider> providersMap;
    private final StoreBillingConfigRepository billingConfigRepository;
    private final NumberingRangeAuditRepository auditRepository;

    @Value("${billing.provider:factus}")
    private String activeProviderName;

    public NumberingRangeServiceImpl(
            List<NumberingRangeProvider> providers,
            StoreBillingConfigRepository billingConfigRepository,
            NumberingRangeAuditRepository auditRepository) {
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
        this.billingConfigRepository = billingConfigRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        return resolveProvider().queryDianNumberingRanges();
    }

    @Override
    public List<NumberingRangeDto> listNumberingRanges() {
        return resolveProvider().listNumberingRanges();
    }

    @Override
    public NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        return resolveProvider().getNumberingRange(numberingRangeId);
    }

    @Override
    @Transactional
    public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        Objects.requireNonNull(request, "La solicitud de rango de numeración no puede ser nula");
        Long storeId = TenantContext.getStoreId();

        guardNoOtherActiveRangeForDocument(storeId, request.document());

        NumberingRangeDto created = resolveProvider().createNumberingRange(request);
        audit(storeId, created.id(), "CREATE", null, describe(created));
        assignAsDefaultIfNoneConfigured(storeId, created);
        return created;
    }

    @Override
    @Transactional
    public NumberingRangeDto setDefaultNumberingRange(Long numberingRangeId) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new BillingNotEnabledException("No hay un local activo para configurar el rango de numeración");
        }
        StoreBillingConfig config = billingConfigRepository.findById(storeId)
                .orElseThrow(() -> new BillingNotEnabledException(
                        "El local no tiene la facturación electrónica habilitada"));

        // El proveedor atiende con las credenciales del propio local, así que si encuentra el
        // rango es porque pertenece a su cuenta: un id de otro local responde "no existe".
        NumberingRangeDto range = resolveProvider().getNumberingRange(numberingRangeId);
        if (range == null) {
            throw new EntityNotFoundException(
                    "El rango de numeración " + numberingRangeId + " no existe en tu cuenta de facturación");
        }
        if (!Boolean.TRUE.equals(range.isActive())) {
            throw new IllegalStateException(
                    "El rango de numeración " + numberingRangeId + " está inactivo. Actívalo antes de usarlo para facturar.");
        }
        if (Boolean.TRUE.equals(range.isExpired())) {
            throw new IllegalStateException(
                    "La resolución del rango de numeración " + numberingRangeId + " ya venció.");
        }

        applyDefault(config, storeId, numberingRangeId);
        return range;
    }

    /** Onboarding: el primer rango que registra un local sin rango configurado queda como su rango activo. */
    private void assignAsDefaultIfNoneConfigured(Long storeId, NumberingRangeDto created) {
        if (storeId == null || created == null || created.id() == null
                || Boolean.FALSE.equals(created.isActive())) {
            return;
        }
        billingConfigRepository.findById(storeId)
                .filter(config -> config.credentialsFor(config.getEnvironment()).getNumberingRangeId() == null)
                .ifPresent(config -> applyDefault(config, storeId, created.id()));
    }

    private void applyDefault(StoreBillingConfig config, Long storeId, Long rangeId) {
        FactusCredentials credentials = config.credentialsFor(config.getEnvironment());
        Integer previous = credentials.getNumberingRangeId();
        credentials.setNumberingRangeId(Math.toIntExact(rangeId));
        billingConfigRepository.save(config);
        audit(storeId, rangeId, "SET_DEFAULT",
                previous != null ? String.valueOf(previous) : null,
                config.getEnvironment() + ":" + rangeId);
        log.info("Local {} usará el rango de numeración {} en {}", storeId, rangeId, config.getEnvironment());
    }

    @Override
    @Transactional
    public void deleteNumberingRange(Long numberingRangeId) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        Long storeId = TenantContext.getStoreId();

        guardNotTheConfiguredRange(storeId, numberingRangeId, "eliminar");

        resolveProvider().deleteNumberingRange(numberingRangeId);
        audit(storeId, numberingRangeId, "DELETE", null, null);
    }

    @Override
    @Transactional
    public NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        Long storeId = TenantContext.getStoreId();

        NumberingRangeDto before = resolveProvider().getNumberingRange(numberingRangeId);
        boolean wasActive = before != null && Boolean.TRUE.equals(before.isActive());

        if (wasActive) {
            // Se va a desactivar: no puede ser el rango que el local usa para facturar.
            guardNotTheConfiguredRange(storeId, numberingRangeId, "desactivar");
        } else if (before != null) {
            // Se va a activar: no puede quedar dos rangos activos para el mismo documento.
            guardNoOtherActiveRangeForDocument(storeId, before.document());
        }

        NumberingRangeDto after = resolveProvider().toggleNumberingRangeStatus(numberingRangeId);
        boolean isActiveNow = after != null && Boolean.TRUE.equals(after.isActive());
        audit(storeId, numberingRangeId, isActiveNow ? "ACTIVATE" : "DEACTIVATE",
                String.valueOf(wasActive), String.valueOf(isActiveNow));
        return after;
    }

    @Override
    @Transactional
    public NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        Objects.requireNonNull(current, "El consecutivo actual es obligatorio");
        Long storeId = TenantContext.getStoreId();

        NumberingRangeDto existing = resolveProvider().getNumberingRange(numberingRangeId);
        if (existing != null && existing.current() != null && current < existing.current()) {
            throw new IllegalArgumentException(
                    "El consecutivo no puede retroceder: el rango va en " + existing.current()
                            + " y se intentó dejarlo en " + current
                            + ". Si necesitas corregirlo hacia atrás, contacta a soporte.");
        }

        NumberingRangeDto updated = resolveProvider().updateNumberingRangeCurrent(numberingRangeId, current);
        audit(storeId, numberingRangeId, "UPDATE_CURRENT",
                existing != null ? String.valueOf(existing.current()) : null, String.valueOf(current));
        return updated;
    }

    /** El rango que el local tiene configurado para facturar no se puede tocar sin elegir otro antes. */
    private void guardNotTheConfiguredRange(Long storeId, Long rangeId, String action) {
        Integer configured = configuredRangeId(storeId);
        if (configured != null && configured.longValue() == rangeId) {
            throw new IllegalStateException(
                    "No puedes " + action + " el rango que tienes configurado como tu rango de numeración "
                            + "activo. Configura otro rango antes de continuar.");
        }
    }

    /** Como máximo un rango activo por tipo de documento (para no dejar ambiguo qué numeración se usa). */
    private void guardNoOtherActiveRangeForDocument(Long storeId, String document) {
        if (storeId == null || document == null) {
            return;
        }
        boolean conflict = resolveProvider().listNumberingRanges().stream()
                .anyMatch(r -> document.equals(r.document()) && Boolean.TRUE.equals(r.isActive()));
        if (conflict) {
            throw new IllegalStateException(
                    "Ya tienes un rango activo para el documento '" + document
                            + "'. Desactívalo antes de activar otro.");
        }
    }

    private Integer configuredRangeId(Long storeId) {
        if (storeId == null) {
            return null;
        }
        return billingConfigRepository.findById(storeId)
                .map(config -> config.credentialsFor(config.getEnvironment()).getNumberingRangeId())
                .orElse(null);
    }

    private void audit(Long storeId, Long rangeId, String action, String previousValue, String newValue) {
        if (storeId == null || rangeId == null) {
            // Sin un local en contexto no hay a quién atribuirle el cambio (p. ej. una llamada de test).
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null ? authentication.getName() : "system";

        NumberingRangeAudit entry = new NumberingRangeAudit();
        entry.setStoreId(storeId);
        entry.setRangeId(rangeId);
        entry.setAction(action);
        entry.setPreviousValue(previousValue);
        entry.setNewValue(newValue);
        entry.setPerformedBy(actor);
        auditRepository.save(entry);
    }

    private static String describe(NumberingRangeDto dto) {
        return dto == null ? null : dto.document() + " " + dto.prefix() + " (" + dto.resolutionNumber() + ")";
    }

    private NumberingRangeProvider resolveProvider() {
        String key = (activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "factus";
        NumberingRangeProvider provider = providersMap.get(key);
        if (provider == null) {
            log.warn("Proveedor de facturación '{}' no encontrado para rangos de numeración. Utilizando 'mock'", key);
            provider = providersMap.get("mock");
        }
        if (provider == null) {
            throw new IllegalStateException("No hay ningún proveedor de rangos de numeración disponible.");
        }
        return provider;
    }
}
