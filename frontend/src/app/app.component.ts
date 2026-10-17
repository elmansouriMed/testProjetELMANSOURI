import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  inject,
  signal,
  viewChild,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Subscription } from "rxjs";
import {
  Filters,
  MessageApi,
  MessageDetail,
  MessagePage,
  MessageSummary,
  Statistics,
} from "./message-api.service";

const emptyFilters = (): Filters => ({
  status: "",
  source: "",
  correlationId: "",
  mqMessageId: "",
  from: "",
  to: "",
});

@Component({
  selector: "app-root",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./app.component.html",
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly api = inject(MessageApi);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  private statsRequest?: Subscription;
  private refreshTimer?: ReturnType<typeof setInterval>;
  readonly dialog =
    viewChild.required<ElementRef<HTMLDialogElement>>("details");
  readonly page = signal<MessagePage>({
    items: [],
    nextCursor: null,
    hasMore: false,
  });
  readonly stats = signal<Statistics | null>(null);
  readonly loading = signal(true);
  readonly error = signal("");
  readonly statsError = signal(false);
  readonly detail = signal<MessageDetail | null>(null);
  readonly selected = signal<MessageSummary | null>(null);
  readonly detailError = signal("");
  readonly notice = signal("");
  readonly lastUpdated = signal<Date | null>(null);
  readonly pageNumber = signal(1);
  filters = emptyFilters();
  private applied = emptyFilters();
  private cursor: string | null = null;
  private history: (string | null)[] = [];
  autoRefresh = false;
  advanced = false;
  ngOnInit() {
    this.load();
    this.refreshTimer = setInterval(() => {
      if (
        this.autoRefresh &&
        !document.hidden &&
        this.pageNumber() === 1 &&
        !this.loading()
      )
        this.load();
    }, 15000);
  }
  ngOnDestroy() {
    this.listRequest?.unsubscribe();
    this.detailRequest?.unsubscribe();
    this.statsRequest?.unsubscribe();
    if (this.refreshTimer) clearInterval(this.refreshTimer);
  }
  apply() {
    const from = this.filters.from ? new Date(this.filters.from) : null;
    const to = this.filters.to ? new Date(this.filters.to) : null;
    if (
      (from && !Number.isFinite(from.getTime())) ||
      (to && !Number.isFinite(to.getTime())) ||
      (from && to && from >= to)
    ) {
      this.error.set(
        "Choisissez une période valide : la date de début doit précéder la date de fin.",
      );
      return;
    }
    this.applied = {
      ...this.filters,
      from: from?.toISOString() ?? "",
      to: to?.toISOString() ?? "",
    };
    this.refresh();
  }
  reset() {
    this.filters = emptyFilters();
    this.applied = emptyFilters();
    this.refresh();
  }
  refresh() {
    this.cursor = null;
    this.history = [];
    this.pageNumber.set(1);
    this.load();
  }
  older() {
    if (!this.page().nextCursor || this.loading()) return;
    this.history.push(this.cursor);
    this.cursor = this.page().nextCursor;
    this.pageNumber.update((n) => n + 1);
    this.load();
  }
  newer() {
    if (!this.history.length || this.loading()) return;
    this.cursor = this.history.pop() ?? null;
    this.pageNumber.update((n) => n - 1);
    this.load();
  }
  private load() {
    this.listRequest?.unsubscribe();
    this.statsRequest?.unsubscribe();
    this.loading.set(true);
    this.error.set("");
    this.listRequest = this.api.list(this.applied, this.cursor).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
        this.lastUpdated.set(new Date());
      },
      error: () => {
        this.page.set({ items: [], nextCursor: null, hasMore: false });
        this.loading.set(false);
        this.error.set(
          "Impossible de charger les messages. Vérifiez la disponibilité de l’API, puis réessayez.",
        );
      },
    });
    this.statsRequest = this.api.statistics().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.statsError.set(false);
      },
      error: () => this.statsError.set(true),
    });
  }
  open(message: MessageSummary) {
    this.detailRequest?.unsubscribe();
    this.selected.set(message);
    this.detail.set(null);
    this.detailError.set("");
    this.notice.set("");
    this.dialog().nativeElement.showModal();
    this.detailRequest = this.api
      .detail(message.id)
      .subscribe({
        next: (detail) => this.detail.set(detail),
        error: () =>
          this.detailError.set(
            "Le détail du message est indisponible. Fermez cette fenêtre et réessayez.",
          ),
      });
  }
  close() {
    this.detailRequest?.unsubscribe();
    this.dialog().nativeElement.close();
    this.selected.set(null);
  }
  backdrop(event: MouseEvent) {
    if (event.target === this.dialog().nativeElement) this.close();
  }
  async copy() {
    const d = this.detail();
    if (!d) return;
    try {
      await navigator.clipboard.writeText(
        d.textPayload ?? d.base64Payload ?? "",
      );
      this.notice.set("Contenu copié.");
    } catch {
      this.notice.set("Copie indisponible. Utilisez le téléchargement.");
    }
  }
  download() {
    const d = this.detail();
    if (!d) return;
    const binary =
      d.base64Payload === null
        ? null
        : Uint8Array.from(atob(d.base64Payload), (c) => c.charCodeAt(0));
    const blob = binary
      ? new Blob([binary], { type: "application/octet-stream" })
      : new Blob([d.textPayload ?? ""], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `message-${d.metadata.id}.${binary ? "bin" : "txt"}`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  bytes(n: number) {
    return n < 1024
      ? `${n} o`
      : n < 1048576
        ? `${(n / 1024).toFixed(1)} Ko`
        : `${(n / 1048576).toFixed(1)} Mo`;
  }
  status(value: string) {
    return value === "STORED" ? "Archivé" : "En quarantaine";
  }
}
