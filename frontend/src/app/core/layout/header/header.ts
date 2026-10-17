// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-header',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<header>
    <div>
      <a routerLink="/messages">Payment Hub</a><span>Consultation des messages de paiement</span>
    </div>
  </header>`,
  styles: `
    header {
      background: #183c50;
      color: white;
    }
    div {
      max-width: 1240px;
      margin: auto;
      min-height: 70px;
      padding: 18px 24px;
      display: flex;
      align-items: center;
      gap: 24px;
    }
    a {
      font-size: 22px;
      font-weight: 650;
      color: inherit;
      text-decoration: none;
    }
    span {
      color: #c6d5dd;
      font-size: 13px;
    }
    @media (max-width: 800px) {
      span {
        display: none;
      }
    }
  `,
})
export class Header {}
