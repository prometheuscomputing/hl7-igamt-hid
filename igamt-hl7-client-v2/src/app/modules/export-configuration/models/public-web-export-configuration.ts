import { IPublicWebExportConfiguration } from './default-export-configuration.interface';

export function publicWebDefaults(): IPublicWebExportConfiguration {
  return { organizationName: '', logoDataUrl: '', logoAlt: '', primaryColor: '#021a4c',
    accentColor: '#F3C60D', linkColor: '#075780', footerText: '', includeExampleLibrary: true };
}

export function contrastOnWhite(color: string): number {
  if (!/^#[0-9a-f]{6}$/i.test(color || '')) { return 0; }
  const linear = [1, 3, 5].map((offset) => {
    const value = parseInt(color.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  });
  const luminance = 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
  return 1.05 / (luminance + 0.05);
}
