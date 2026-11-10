// DIAN codes used by a debit note that adjusts a validated electronic invoice.
export const DEBIT_NOTE_DIAN = {
  /** Customization 30: debit note that references an electronic invoice. */
  CUSTOMIZATION_WITH_INVOICE_REFERENCE: '30',
  /** Payment form 1: cash (contado). */
  PAYMENT_FORM_CASH: '1',
  /** Payment method 10: cash (efectivo). */
  PAYMENT_METHOD_CASH: '10',
  /** Unit of measure 94: unit. */
  UNIT_MEASURE_UNIT: '94',
  /** Standard code 999: the seller's own product code. */
  STANDARD_CODE_OWN: '999',
  /** Tax 01: IVA. */
  TAX_IVA: '01',
};

/** Internal code of the single adjustment line a manual debit note carries. */
export const ADJUSTMENT_ITEM_CODE = 'ADJUSTMENT';

/**
 * Body of POST /billing/debit-notes/sales/{saleId} for a single adjustment line. The reference code is kept by the
 * caller so a retry of the same request never creates a second note.
 */
export function buildDebitNoteRequest({ reference, concept, invoice, observation, description, quantity, price, taxRate }) {
  return {
    reference_code: reference,
    correction_concept_code: concept,
    customization_id: DEBIT_NOTE_DIAN.CUSTOMIZATION_WITH_INVOICE_REFERENCE,
    bill_number: invoice.legalNumber,
    observation: observation.trim(),
    customer: null, // the backend takes the customer of the original sale
    payment_details: [{ payment_form: DEBIT_NOTE_DIAN.PAYMENT_FORM_CASH, payment_method_code: DEBIT_NOTE_DIAN.PAYMENT_METHOD_CASH }],
    items: [{
      code_reference: ADJUSTMENT_ITEM_CODE,
      name: description.trim(),
      quantity,
      price,
      discount_rate: '0',
      unit_measure_code: DEBIT_NOTE_DIAN.UNIT_MEASURE_UNIT,
      standard_code: DEBIT_NOTE_DIAN.STANDARD_CODE_OWN,
      taxes: [{ code: DEBIT_NOTE_DIAN.TAX_IVA, rate: taxRate }],
    }],
  };
}

/**
 * A 400 means the backend rejected the request before creating anything, so the form can be corrected. Any other
 * failure (timeout, network, 5xx) is uncertain: the note may exist, so the same request must be retried unchanged.
 */
export function isDefiniteRejection(error) {
  return error?.status === 400;
}

/** The note's legal number once the DIAN accepts it; before that, its reference or its id. */
export function noteNumber(note) {
  return note.creditNoteNumber || note.debitNoteNumber || note.referenceCode || `#${note.id}`;
}
