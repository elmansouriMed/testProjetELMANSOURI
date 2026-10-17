// createdBy ELMANSOURI Mohamed
import { MessageDetail } from '../models/message.model';
import { SAMPLE_MESSAGE } from '../testing/message.fixture';
import { messageBlob } from './message-download';

function readBytes(blob: Blob): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(blob);
  });
}

describe('Téléchargement du contenu', () => {
  it('préserve les octets binaires, y compris les valeurs nulles et non ASCII', async () => {
    const detail: MessageDetail = {
      metadata: SAMPLE_MESSAGE,
      textPayload: null,
      base64Payload: 'AID/DQo=',
    };
    expect(await readBytes(messageBlob(detail))).toEqual(new Uint8Array([0, 128, 255, 13, 10]));
  });

  it('encode les caractères accentués et le symbole euro en UTF-8', async () => {
    const text = 'Paiement réglé : 125 €';
    const detail: MessageDetail = {
      metadata: SAMPLE_MESSAGE,
      textPayload: text,
      base64Payload: null,
    };
    expect(Array.from(await readBytes(messageBlob(detail)))).toEqual(
      Array.from(new TextEncoder().encode(text)),
    );
  });
});
