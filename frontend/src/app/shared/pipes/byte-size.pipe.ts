// createdBy ELMANSOURI Mohamed
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'byteSize' })
export class ByteSizePipe implements PipeTransform {
  transform(size: number): string {
    if (size < 1024) return `${size} o`;
    if (size < 1048576) return `${(size / 1024).toFixed(1)} Ko`;
    return `${(size / 1048576).toFixed(1)} Mo`;
  }
}
