// createdBy ELMANSOURI Mohamed
// Donnée commune aux tests ; aucune donnée de paiement réelle.
import { MessageSummary } from '../models/message.model';

export const SAMPLE_MESSAGE: MessageSummary = {
  id: '43',
  mqMessageId: 'ID:43',
  correlationId: 'paiement-43',
  sourceApplication: 'BO-SEPA',
  sourceQueue: 'DEV.PAYMENTS.IN',
  sentAt: null,
  receivedAt: '2026-10-17T10:00:00Z',
  bodyType: 'TEXT',
  payloadSize: 12,
  redelivered: false,
  status: 'STORED',
  rejectionReason: null,
};
