import { Injectable, inject } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";

export interface MessageSummary {
  id: string;
  mqMessageId: string;
  correlationId: string | null;
  sourceApplication: string;
  sourceQueue: string;
  sentAt: string | null;
  receivedAt: string;
  bodyType: string;
  payloadSize: number;
  redelivered: boolean;
  status: "STORED" | "QUARANTINED";
  rejectionReason: string | null;
}
export interface MessageDetail {
  metadata: MessageSummary;
  textPayload: string | null;
  base64Payload: string | null;
}
export interface MessagePage {
  items: MessageSummary[];
  nextCursor: string | null;
  hasMore: boolean;
}
export interface Statistics {
  received24h: number;
  quarantined24h: number;
  received5m: number;
  bytes24h: number;
  sampledAt: string;
  mqEnabled: boolean;
  listenerRunning: boolean;
  recovering: boolean;
}
export interface Filters {
  status: string;
  source: string;
  correlationId: string;
  mqMessageId: string;
  from: string;
  to: string;
}

@Injectable({ providedIn: "root" })
export class MessageApi {
  private readonly http = inject(HttpClient);
  list(filters: Filters, cursor: string | null, limit = 25) {
    let params = new HttpParams().set("limit", limit);
    for (const [key, value] of Object.entries(filters))
      if (value.trim()) params = params.set(key, value.trim());
    if (cursor) params = params.set("beforeId", cursor);
    return this.http.get<MessagePage>("/api/messages", { params });
  }
  detail(id: string) {
    return this.http.get<MessageDetail>(
      `/api/messages/${encodeURIComponent(id)}`,
    );
  }
  statistics() {
    return this.http.get<Statistics>("/api/messages/statistics");
  }
}
