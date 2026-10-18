// createdBy ELMANSOURI Mohamed
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);
  if (!request.url.startsWith(`${baseUrl}/`)) return next(request);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
      const message =
        error.status === 0
          ? 'La connexion au serveur est indisponible.'
          : error.status === 400
            ? 'Les paramètres de recherche sont invalides.'
            : error.status === 404
              ? 'Le message demandé est introuvable.'
              : error.status === 409
                ? 'Le message a été modifié. Actualisez les données avant de réessayer.'
                : error.status === 503
                  ? 'Le stockage est temporairement indisponible. Réessayez dans quelques instants.'
                  : 'Une erreur est survenue. Réessayez dans quelques instants.';
      return throwError(() => new ApiError(error.status, message));
    }),
  );
};
