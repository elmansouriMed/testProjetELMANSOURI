// createdBy ELMANSOURI Mohamed
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '@core/config/api.config';
import { MessageDetail, MessageFilters, MessagePage } from '../models/message.model';

@Injectable({ providedIn: 'root' })
export class MessagesApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/messages`;
  list(filters: MessageFilters, cursor: string | null, limit = 25): Observable<MessagePage> {
    let params = new HttpParams().set('limit', limit);
    for (const [key, value] of Object.entries(filters))
      if (value.trim()) params = params.set(key, value.trim());
    if (cursor) params = params.set('beforeId', cursor);
    return this.http.get<MessagePage>(this.url, { params });
  }
  detail(id: string): Observable<MessageDetail> {
    return this.http.get<MessageDetail>(`${this.url}/${encodeURIComponent(id)}`);
  }
}
