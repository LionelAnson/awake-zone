import { expect, it } from 'vitest';
import { textColorForPixels } from '../src/wallpaper-contrast';

it.each([
  [0, 'white'], [40, 'white'], [100, 'white'],
  [128, 'black'], [180, 'black'], [255, 'black'],
] as const)('uses the requested text color for gray level %i', (value, expected) => {
  expect(textColorForPixels(new Uint8ClampedArray([value, value, value, 255]))).toBe(expected);
});
it('accounts for colored backgrounds and mixed regions', () => {
  expect(textColorForPixels(new Uint8ClampedArray([0, 0, 255, 255]))).toBe('white');
  expect(textColorForPixels(new Uint8ClampedArray([0, 255, 0, 255]))).toBe('black');
  expect(textColorForPixels(new Uint8ClampedArray([0, 0, 0, 255, 255, 255, 255, 255]))).toBe('black');
});
