package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigService;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Dos solicitudes que activan a la vez la facturación del mismo local (por ejemplo, dos consultas del asistente que
 * ven todo listo al mismo tiempo): la transición toma el lock de la fila, así que solo una activa y avisa al local.
 */
@SpringBootTest(properties = "billing.provider=mock")
class StoreBillingActivationConcurrencyTest {

    private static final Long STORE = 770_001L;

    @Autowired
    private StoreBillingConfigService service;
    @Autowired
    private StoreBillingConfigRepository repository;
    @MockitoBean
    private NotificationFacade notifications;

    @AfterEach
    void cleanUp() {
        repository.deleteById(STORE);
    }

    private void storeInSetup() {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(STORE);
        config.setStatus(BillingActivationStatus.IN_SETUP);
        config.setEnvironment(BillingEnvironment.PRODUCTION);
        config.assignCompanyId(BillingEnvironment.PRODUCTION, "mock-company-900123456");
        repository.saveAndFlush(config);
    }

    @Test
    void twoSimultaneousActivationsActivateOnceAndNotifyOnce() throws Exception {
        storeInSetup();
        List<CreateNotificationCommand> sent = new CopyOnWriteArrayList<>();
        // La notificación ocurre dentro de la transacción de la activación: sin lock, las dos llegan aquí a la vez.
        CyclicBarrier bothInside = new CyclicBarrier(2);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            try {
                bothInside.await(1, TimeUnit.SECONDS);
            } catch (TimeoutException | java.util.concurrent.BrokenBarrierException alone) {
                // con el lock la otra espera fuera de la sección: se sigue sin ella
            }
            return null;
        }).when(notifications).createNotification(any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<Boolean> activate = () -> {
                start.await();
                try {
                    service.transition(STORE, BillingActivationStatus.ACTIVE, null, "system");
                    return true;
                } catch (IllegalStateException alreadyActive) {
                    return false;
                }
            };
            results.add(pool.submit(activate));
        }
        start.countDown();
        int activations = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) activations++;
        }
        pool.shutdown();

        assertEquals(1, activations, "solo una transición pasa el local a ACTIVE");
        assertEquals(1, sent.size(), "el local recibe un solo aviso de activación");
        assertEquals(BillingActivationStatus.ACTIVE, repository.findById(STORE).orElseThrow().getStatus());
        assertTrue(sent.get(0).message().toLowerCase().contains("activ"), sent.get(0).message());
    }
}
