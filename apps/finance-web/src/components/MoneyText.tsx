import { useMemo } from 'react';
import type { ReactNode } from 'react';

const DECIMAL_PATTERN = /^-?\d+(\.\d+)?$/;

/**
 * Displays an authoritative decimal string without ever parsing it as a JS number.
 * Locale grouping is applied digit-wise so the value stays exact, and the raw string is
 * exposed in the title attribute and DOM attribute for verification.
 */
export function MoneyText({
  value,
  currency,
  locale,
  scale,
}: {
  value: string;
  currency: string;
  locale: string;
  scale: number;
}): ReactNode {
  const display = useMemo(() => {
    if (!DECIMAL_PATTERN.test(value)) {
      return value;
    }
    const negative = value.startsWith('-');
    const unsigned = negative ? value.slice(1) : value;
    const parts = unsigned.split('.');
    const integerPart: string = parts[0] ?? '0';
    const fractionPart: string = parts[1] ?? '';
    const grouped = new Intl.NumberFormat(locale, { useGrouping: true }).format(
      BigInt(integerPart),
    );
    const fraction = fractionPart.padEnd(scale, '0').slice(0, scale);
    const sign = negative ? '-' : '';
    return fraction.length > 0
      ? `${sign}${grouped}.${fraction} ${currency}`
      : `${sign}${grouped} ${currency}`;
  }, [value, currency, locale, scale]);

  return (
    <span className="money" data-raw={value} title={`${value} ${currency}`}>
      {display}
    </span>
  );
}
