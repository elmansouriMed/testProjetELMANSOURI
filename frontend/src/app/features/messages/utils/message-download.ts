// createdBy ELMANSOURI Mohamed
import { MessageDetail } from '../models/message.model';

export function messageBlob(message: MessageDetail): Blob {
  if (message.base64Payload !== null) {
    const bytes = Uint8Array.from(atob(message.base64Payload), (character) =>
      character.charCodeAt(0),
    );
    return new Blob([bytes], { type: 'application/octet-stream' });
  }
  return new Blob([message.textPayload ?? ''], {
    type: 'text/plain;charset=utf-8',
  });
}
export function downloadMessage(message: MessageDetail): void {
  const url = URL.createObjectURL(messageBlob(message));
  const link = document.createElement('a');
  link.href = url;
  link.download = `message-${message.metadata.id}.${message.base64Payload !== null ? 'bin' : 'txt'}`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
