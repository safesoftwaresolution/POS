package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.voicesales.VoiceAgentRequest.Line;
import com.sciencebot.pos.voicesales.VoiceAgentRequest.Ticket;
import com.sciencebot.pos.voicesales.VoiceAgentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static com.sciencebot.pos.voicesales.internal.agent.HarnessFixture.*;
import static com.sciencebot.pos.voicesales.internal.agent.ScriptedChatModel.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spanish utterances a cashier really says, with the ticket each must leave. The same list drives two runs:
 * the scripted one replays {@code script} (what a good model would do) and always runs offline; the live one
 * ignores {@code script}, lets a real model decide, and checks the same expectations.
 */
final class HarnessCases {
    /** @param expect assertions on the final response; the same ones apply to a live model. */
    record HarnessCase(String name, String utterance, Ticket start, ScriptedChatModel.Step[] script,
                       Consumer<VoiceAgentResponse> expect) {
        @Override
        public String toString() {
            return name;
        }
    }

    private HarnessCases() {}

    private static Ticket ticket(Line... lines) {
        return new Ticket(List.of(lines), null, null, null);
    }

    private static List<String> summary(VoiceAgentResponse response) {
        return response.ticket().lines().stream().map(l -> l.name() + " x" + l.quantity()).toList();
    }

    private static void defaultCustomer(VoiceAgentResponse response) {
        assertThat(response.ticket().customer()).isNotNull();
        assertThat(response.ticket().customer().id()).isEqualTo(GENERAL);
    }

    static List<HarnessCase> all() {
        return List.of(
                new HarnessCase("invoice with two products and payment method",
                        "Hazme una factura con dos cafés grandes y una agua, paga con Nequi", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":2}"),
                                        call("agregarArticulo", "{\"nombre\":\"Agua\",\"cantidad\":1}"),
                                        call("fijarMedioPago", "{\"medio\":\"Nequi\"}")),
                                say("Listo: 2 cafés grandes y 1 agua, pago con Nequi.")},
                        r -> {
                            assertThat(summary(r)).containsExactlyInAnyOrder("Café grande x2", "Agua x1");
                            assertThat(r.ticket().paymentMethod().code()).isEqualTo("NEQUI");
                            defaultCustomer(r);
                            assertThat(r.ticket().confirmationRequested()).isFalse();
                        }),
                new HarnessCase("charge with cash received asks for confirmation",
                        "Cóbrame un café grande en efectivo, me dan cincuenta mil", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":1}"),
                                        call("fijarMedioPago", "{\"medio\":\"efectivo\"}"),
                                        call("fijarEfectivoRecibido", "{\"monto\":50000}"),
                                        call("pedirConfirmacion", "{}")),
                                say("Un café grande, efectivo, recibes 50.000. ¿Confirmas?")},
                        r -> {
                            assertThat(summary(r)).containsExactly("Café grande x1");
                            assertThat(r.ticket().paymentMethod().code()).isEqualTo("CASH");
                            assertThat(r.ticket().cashReceived()).isEqualByComparingTo(new BigDecimal("50000"));
                            assertThat(r.ticket().confirmationRequested()).isTrue();
                        }),
                new HarnessCase("ambiguous item is not guessed",
                        "Agrégame una gaseosa", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Gaseosa\",\"cantidad\":1}")),
                                say("¿Gaseosa cola o gaseosa limón?")},
                        r -> assertThat(r.ticket().lines()).isEmpty()),
                new HarnessCase("not enough stock leaves the ticket untouched",
                        "Ponme cinco aguas", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Agua\",\"cantidad\":5}")),
                                say("Solo hay 2 aguas disponibles. ¿Pongo 2?")},
                        r -> assertThat(r.ticket().lines()).isEmpty()),
                new HarnessCase("remove an item by name",
                        "Quita el agua", ticket(new Line("PRODUCT", COFFEE, 2), new Line("PRODUCT", WATER, 1)),
                        new ScriptedChatModel.Step[]{
                                calls(call("quitarArticulo", "{\"nombre\":\"Agua\"}")), say("Quité el agua.")},
                        r -> assertThat(summary(r)).containsExactly("Café grande x2")),
                new HarnessCase("change a quantity",
                        "Cambia los cafés a tres", ticket(new Line("PRODUCT", COFFEE, 2)),
                        new ScriptedChatModel.Step[]{
                                calls(call("cambiarCantidad", "{\"nombre\":\"Café grande\",\"cantidad\":3}")),
                                say("Ahora son 3 cafés grandes.")},
                        r -> assertThat(summary(r)).containsExactly("Café grande x3")),
                new HarnessCase("named customer and a service",
                        "Un corte a nombre de Luis Gómez", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Corte\",\"cantidad\":1}"),
                                        call("asignarCliente", "{\"cliente\":\"Luis Gómez\"}")),
                                say("Un corte para Luis Gómez.")},
                        r -> {
                            assertThat(summary(r)).containsExactly("Corte x1");
                            assertThat(r.ticket().lines().getFirst().kind()).isEqualTo("SERVICE");
                            assertThat(r.ticket().customer().id()).isEqualTo(LUIS);
                        }),
                new HarnessCase("homonym customer keeps the general customer",
                        "Un café grande para Ana Pérez", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":1}"),
                                        call("asignarCliente", "{\"cliente\":\"Ana Pérez\"}")),
                                say("Hay dos Ana Pérez, ¿cuál es?")},
                        r -> {
                            assertThat(summary(r)).containsExactly("Café grande x1");
                            defaultCustomer(r);
                        }),
                new HarnessCase("clear everything",
                        "Borra todo", ticket(new Line("PRODUCT", COFFEE, 2), new Line("SERVICE", HAIRCUT, 1)),
                        new ScriptedChatModel.Step[]{calls(call("vaciarTicket", "{}")), say("Ticket vacío.")},
                        r -> {
                            assertThat(r.ticket().lines()).isEmpty();
                            assertThat(r.ticket().paymentMethod()).isNull();
                        }),
                new HarnessCase("asking for an electronic invoice never emits one",
                        "Hazme una factura electrónica DIAN de un café grande", null,
                        new ScriptedChatModel.Step[]{
                                calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":1}")),
                                say("Agregué el café. Desde aquí solo armo ventas normales; la factura electrónica se "
                                        + "elige al cobrar.")},
                        r -> {
                            assertThat(summary(r)).containsExactly("Café grande x1");
                            assertThat(r.ticket().confirmationRequested()).isFalse();
                        })
        );
    }
}
