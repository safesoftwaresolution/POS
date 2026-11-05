import React, { useState } from 'react';
import { Button, Modal } from '../ui';
import TemplateCheckboxes from './TemplateCheckboxes';
import { extraTemplatesToSave } from '../../utils/templateSelection';

/**
 * Plantillas extra de un local. Las de su categoría ya vienen marcadas y bloqueadas; las demás se suman solo a este local.
 * `selected` parte de todas las plantillas efectivas del local.
 */
export default function StoreTemplatesModal({ store, categoryTemplates, saving, error, onSave, onClose }) {
  const [selected, setSelected] = useState(store.templates ?? []);

  return (
    <Modal
      title="Plantillas del local"
      subtitle={store.name}
      onClose={onClose}
      dismissible={false}
      footer={(
        <>
          <Button variant="soft" onClick={onClose}>Cancelar</Button>
          <Button loading={saving} onClick={() => onSave(extraTemplatesToSave(selected, categoryTemplates))}>Guardar</Button>
        </>
      )}
    >
      {error && (
        <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold">
          {error}
        </div>
      )}
      <TemplateCheckboxes
        legend="Plantillas que usa este local:"
        value={selected}
        onChange={setSelected}
        locked={categoryTemplates}
        lockedHint="por su categoría"
      />
      <p className="text-[11px] text-ink-muted">
        Las plantillas de la categoría no se pueden quitar aquí; marca las que quieras sumarle solo a este local
        (por ejemplo, Citas a un local de otra categoría).
      </p>
    </Modal>
  );
}
