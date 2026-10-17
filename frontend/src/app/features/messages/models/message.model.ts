// createdBy ELMANSOURI Mohamed
export type MessageStatus = 'STORED' | 'QUARANTINED';
export type BodyType = 'TEXT' | 'BYTES' | 'UNSUPPORTED';

export interface MessageSummary {
  readonly id: string;
  readonly mqMessageId: string;
  readonly correlationId: string | null;
  readonly sourceApplication: string;
  readonly sourceQueue: string;
  readonly sentAt: string | null;
  readonly receivedAt: string;
  readonly bodyType: BodyType;
  readonly payloadSize: number;
  readonly redelivered: boolean;
  readonly status: MessageStatus;
  readonly rejectionReason: string | null;
}
export interface MessageDetail {
  readonly metadata: MessageSummary;
  readonly textPayload: string | null;
  readonly base64Payload: string | null;
}
export interface MessagePage {
  readonly items: readonly MessageSummary[];
  readonly nextCursor: string | null;
  readonly hasMore: boolean;
}
export interface MessageFilters {
  readonly status: MessageStatus | '';
  readonly source: string;
  readonly correlationId: string;
  readonly mqMessageId: string;
  readonly from: string;
  readonly to: string;
}
export interface LoadState<T> {
  readonly data: T | null;
  readonly loading: boolean;
  readonly error: string | null;
}
export const EMPTY_FILTERS: MessageFilters = {
  status: '',
  source: '',
  correlationId: '',
  mqMessageId: '',
  from: '',
  to: '',
};
export const EMPTY_PAGE: MessagePage = {
  items: [],
  nextCursor: null,
  hasMore: false,
};
