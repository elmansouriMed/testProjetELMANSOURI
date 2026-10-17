import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import {
  provideHttpClientTesting,
  HttpTestingController,
} from "@angular/common/http/testing";
import { MessageApi } from "./message-api.service";

describe("MessageApi", () => {
  let api: MessageApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(MessageApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it("transmet les filtres exacts et le curseur sans perdre les caractères spéciaux", () => {
    api
      .list(
        {
          status: "STORED",
          source: " BO-SEPA ",
          correlationId: "ref+€&42",
          mqMessageId: "",
          from: "",
          to: "",
        },
        "9007199254740993",
      )
      .subscribe();
    const req = http.expectOne((r) => r.url === "/api/messages");
    expect(req.request.params.get("source")).toBe("BO-SEPA");
    expect(req.request.params.get("correlationId")).toBe("ref+€&42");
    expect(req.request.params.get("beforeId")).toBe("9007199254740993");
    expect(req.request.params.has("mqMessageId")).toBe(false);
    req.flush({ items: [], nextCursor: null, hasMore: false });
  });
  it("propage une panne API pour permettre un état erreur dans l’interface", () => {
    let status = 0;
    api.detail("42").subscribe({ error: (e) => (status = e.status) });
    http
      .expectOne("/api/messages/42")
      .flush({}, { status: 503, statusText: "Service Unavailable" });
    expect(status).toBe(503);
  });
});
