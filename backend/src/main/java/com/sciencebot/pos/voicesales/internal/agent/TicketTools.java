package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Candidate;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Reference;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Status;
import com.sciencebot.pos.voicesales.VoiceTicketDto;
import com.sciencebot.pos.voicesales.internal.ports.SaleDraftResolver;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * What the model may do to the ticket. One instance per turn, bound to that turn's {@link TicketDraft}.
 *
 * <p>The model only passes literal mentions. Items, customers and payment methods are resolved against the
 * authenticated store, prices come from the catalog, and there is deliberately no tool that registers a sale:
 * {@link #pedirConfirmacion()} only raises a flag so the POS shows its confirmation bar.
 */
final class TicketTools {
    static final String GENERAL_CUSTOMER_IDENTIFICATION = "9999999999";
    static final int MAX_QUANTITY = SaleIntent.MAX_QUANTITY;
    private static final int MAX_OPTIONS = 5;

    private final TicketDraft draft;
    private final SaleDraftResolver resolver;
    private final ProductFacade products;
    private final ServiceFacade services;
    private final CustomerFacade customers;
    private final SettingsFacade settings;

    TicketTools(TicketDraft draft, SaleDraftResolver resolver, ProductFacade products, ServiceFacade services,
                CustomerFacade customers, SettingsFacade settings) {
        this.draft = draft;
        this.resolver = resolver;
        this.products = products;
        this.services = services;
        this.customers = customers;
        this.settings = settings;
    }

    @Tool(description = "Agrega un producto o servicio al ticket. Usa el nombre tal como lo dijo el cajero. "
            + "Si ya está en el ticket suma la cantidad. El precio sale del catálogo, nunca lo inventes.")
    public String agregarArticulo(@ToolParam(description = "Nombre del artículo") String nombre,
                                  @ToolParam(description = "Cantidad entera, de 1 a 1000") Integer cantidad) {
        if (cantidad == null || cantidad < 1 || cantidad > MAX_QUANTITY) {
            return "La cantidad debe estar entre 1 y " + MAX_QUANTITY + ".";
        }
        Reference reference = resolver.resolve(new SaleIntent(List.of(new SaleIntent.Item(nombre, cantidad)),
                null, null, null)).items().getFirst().item();
        Candidate candidate;
        if (reference.status() == Status.RESOLVED) {
            candidate = reference.candidates().getFirst();
        } else {
            // Not an exact name: try the way people talk ("aceite de girasol de un litro") before giving up.
            var loose = LooseMatcher.items(products, services, nombre);
            if (!loose.unique()) {
                return notResolved("el artículo", nombre,
                        loose.best().isEmpty() ? reference.candidates() : loose.best());
            }
            candidate = loose.best().getFirst();
        }
        return candidate.kind() == Kind.SERVICE ? addService(candidate, cantidad) : addProduct(candidate, cantidad);
    }

    private String addProduct(Candidate candidate, int quantity) {
        var product = products.getById(candidate.id()).filter(p -> p.active() && p.salePrice() != null);
        if (product.isEmpty()) {
            return "El artículo «" + candidate.label() + "» ya no está disponible.";
        }
        var found = product.get();
        var existing = draft.line(Kind.PRODUCT.name(), found.id());
        int wanted = quantity + (existing == null ? 0 : existing.quantity());
        if (wanted > found.quantityAvailable()) {
            return "Solo hay " + found.quantityAvailable() + " disponibles de " + found.name()
                    + ". No lo agregué al ticket.";
        }
        draft.putLine(new VoiceTicketDto.Line(Kind.PRODUCT.name(), found.id(), found.internalCode(), found.name(),
                found.salePrice(), wanted));
        return "Agregado: " + wanted + " × " + found.name() + ".";
    }

    private String addService(Candidate candidate, int quantity) {
        var service = services.getById(candidate.id()).filter(s -> s.active() && s.price() != null);
        if (service.isEmpty()) {
            return "El servicio «" + candidate.label() + "» ya no está disponible.";
        }
        var found = service.get();
        var existing = draft.line(Kind.SERVICE.name(), found.id());
        int wanted = quantity + (existing == null ? 0 : existing.quantity());
        if (wanted > MAX_QUANTITY) {
            return "La cantidad debe estar entre 1 y " + MAX_QUANTITY + ".";
        }
        draft.putLine(new VoiceTicketDto.Line(Kind.SERVICE.name(), found.id(), found.code(), found.name(),
                found.price(), wanted));
        return "Agregado: " + wanted + " × " + found.name() + ".";
    }

    @Tool(description = "Quita un artículo del ticket por su nombre.")
    public String quitarArticulo(@ToolParam(description = "Nombre del artículo que está en el ticket") String nombre) {
        var matches = inTicket(nombre);
        if (matches.size() != 1) {
            return ticketMatchProblem(nombre, matches);
        }
        draft.removeLine(matches.getFirst());
        return "Quitado: " + matches.getFirst().name() + ".";
    }

    @Tool(description = "Cambia la cantidad de un artículo que ya está en el ticket (no suma, reemplaza).")
    public String cambiarCantidad(@ToolParam(description = "Nombre del artículo que está en el ticket") String nombre,
                                  @ToolParam(description = "Nueva cantidad entera, de 1 a 1000") Integer cantidad) {
        if (cantidad == null || cantidad < 1 || cantidad > MAX_QUANTITY) {
            return "La cantidad debe estar entre 1 y " + MAX_QUANTITY + ".";
        }
        var matches = inTicket(nombre);
        if (matches.size() != 1) {
            return ticketMatchProblem(nombre, matches);
        }
        var line = matches.getFirst();
        if (Kind.PRODUCT.name().equals(line.kind())) {
            var product = products.getById(line.id());
            if (product.isPresent() && cantidad > product.get().quantityAvailable()) {
                return "Solo hay " + product.get().quantityAvailable() + " disponibles de " + line.name()
                        + ". No cambié la cantidad.";
            }
        }
        draft.putLine(new VoiceTicketDto.Line(line.kind(), line.id(), line.code(), line.name(), line.unitPrice(), cantidad));
        return "Ahora son " + cantidad + " × " + line.name() + ".";
    }

    @Tool(description = "Asigna el cliente de la venta. Si el cajero no menciona cliente, no llames esta herramienta: "
            + "se usa Cliente General.")
    public String asignarCliente(@ToolParam(description = "Nombre o documento del cliente") String cliente) {
        Reference reference = resolver.resolve(new SaleIntent(null, cliente, null, null)).customer();
        if (reference.status() != Status.RESOLVED) {
            return notResolved("el cliente", cliente, reference);
        }
        var found = customers.getById(reference.candidates().getFirst().id()).filter(c -> c.active());
        if (found.isEmpty()) {
            return "El cliente «" + cliente + "» ya no está disponible.";
        }
        draft.customer(new VoiceTicketDto.Customer(found.get().id(), found.get().fullName()));
        return "Cliente: " + found.get().fullName() + ".";
    }

    @Tool(description = "Vuelve a Cliente General (consumidor final).")
    public String usarClienteGeneral() {
        draft.customer(null);
        return "Cliente: Cliente General.";
    }

    @Tool(description = "Fija el medio de pago (efectivo, tarjeta, Nequi, crédito...) tal como lo dijo el cajero.")
    public String fijarMedioPago(@ToolParam(description = "Medio de pago") String medio) {
        Reference reference = resolver.resolve(new SaleIntent(null, null, medio, null)).paymentMethod();
        Candidate candidate;
        if (reference.status() == Status.RESOLVED) {
            candidate = reference.candidates().getFirst();
        } else {
            // A misheard word ("Neki" for "Nequi") is a typo away from a configured method.
            var loose = LooseMatcher.paymentMethod(settings, medio);
            if (!loose.unique()) {
                return notResolved("el medio de pago", medio,
                        loose.best().isEmpty() ? reference.candidates() : loose.best());
            }
            candidate = loose.best().getFirst();
        }
        draft.paymentMethod(new VoiceTicketDto.PaymentMethod(candidate.code(), candidate.label()));
        return "Medio de pago: " + candidate.label() + ".";
    }

    @Tool(description = "Fija el efectivo que entrega el cliente, solo si el cajero lo dijo.")
    public String fijarEfectivoRecibido(@ToolParam(description = "Monto en pesos, sin decimales si es entero") BigDecimal monto) {
        if (monto == null || monto.signum() < 0 || monto.compareTo(SaleIntent.MAX_CASH) > 0
                || monto.stripTrailingZeros().scale() > 2) {
            return "El monto no es válido.";
        }
        draft.cashReceived(monto);
        return "Efectivo recibido: " + monto.stripTrailingZeros().toPlainString() + ".";
    }

    @Tool(description = "Vacía el ticket completo (artículos, cliente, pago y efectivo).")
    public String vaciarTicket() {
        draft.clear();
        return "Ticket vacío.";
    }

    @Tool(description = "Úsala cuando el cajero pide cobrar o registrar la venta. NO registra nada: solo pide al "
            + "cajero que confirme en pantalla lo que quedó en el ticket.")
    public String pedirConfirmacion() {
        if (draft.lines().isEmpty()) {
            return "El ticket está vacío: no hay nada que confirmar.";
        }
        draft.requestConfirmation();
        return "Listo: el cajero verá la confirmación en pantalla.";
    }

    /** Snapshot for the POS. Without an explicit customer the store's Cliente General applies. */
    VoiceTicketDto ticket() {
        var customer = draft.customer() != null ? draft.customer()
                : customers.getByIdentification(GENERAL_CUSTOMER_IDENTIFICATION)
                        .map(c -> new VoiceTicketDto.Customer(c.id(), c.fullName())).orElse(null);
        var lines = draft.lines();
        BigDecimal subtotal = lines.stream().map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VoiceTicketDto(List.copyOf(lines), customer, draft.paymentMethod(), draft.cashReceived(), subtotal,
                draft.confirmationRequested());
    }

    private List<VoiceTicketDto.Line> inTicket(String name) {
        String wanted = normalize(name);
        if (wanted.isEmpty()) {
            return List.of();
        }
        var all = draft.lines();
        var exact = all.stream().filter(l -> normalize(l.name()).equals(wanted)).toList();
        if (exact.size() == 1) {
            return exact;
        }
        return all.stream().filter(l -> normalize(l.name()).contains(wanted)).toList();
    }

    private static String ticketMatchProblem(String name, List<VoiceTicketDto.Line> matches) {
        if (matches.isEmpty()) {
            return "No hay «" + name + "» en el ticket.";
        }
        return "Hay varios artículos que coinciden con «" + name + "»: "
                + matches.stream().map(VoiceTicketDto.Line::name).collect(Collectors.joining(", "))
                + ". Dime cuál.";
    }

    private static String notResolved(String what, String mention, Reference reference) {
        return notResolved(what, mention, reference.candidates());
    }

    private static String notResolved(String what, String mention, List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return "No encontré " + what + " «" + mention + "». Pídele al cajero que lo repita o que lo agregue a mano.";
        }
        String options = candidates.stream().limit(MAX_OPTIONS).map(Candidate::label)
                .collect(Collectors.joining("; "));
        return "«" + mention + "» no es exacto. Opciones: " + options
                + ". Pregunta al cajero cuál es o repite la herramienta con el nombre exacto.";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
