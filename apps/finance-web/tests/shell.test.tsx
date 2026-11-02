import { readFileSync } from 'node:fs';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MoneyText } from '../src/components/MoneyText';
import { AppShell } from '../src/app/AppShell';

describe('MoneyText', () => {
  it('preserves the exact decimal string including scale for verification', () => {
    render(<MoneyText value="1250000.00" currency="USD" locale="en-US" scale={2} />);
    const element = screen.getByText('1,250,000.00 USD');
    expect(element).toHaveAttribute('data-raw', '1250000.00');
    expect(element).toHaveAttribute('title', '1250000.00 USD');
  });

  it('keeps sign and pads or trims to the declared scale without floating point', () => {
    render(<MoneyText value="-75.5" currency="USD" locale="en-US" scale={2} />);
    expect(screen.getByText('-75.50 USD')).toBeInTheDocument();
  });

  it('never renders scientific notation or number-coerced values', () => {
    render(<MoneyText value="0.00000001" currency="EUR" locale="de-DE" scale={8} />);
    const element = screen.getByTitle('0.00000001 EUR');
    expect(element.textContent).not.toContain('e-');
    expect(element).toHaveAttribute('data-raw', '0.00000001');
  });
});

describe('AppShell accessibility', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders a skip link pointing at the main landmark before any nav', () => {
    render(<AppShell />);
    const skip = screen.getByRole('link', { name: 'Skip to main content' });
    expect(skip).toBeInTheDocument();
    expect(skip).toHaveAttribute('href', '#main-content');
    expect(skip.compareDocumentPosition(screen.getByRole('banner'))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('exposes landmarks and navigates by keyboard between sections', async () => {
    const user = userEvent.setup();
    render(<AppShell />);
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeInTheDocument();
    expect(screen.getByRole('main', { name: 'Main content' })).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();

    const reportsButton = screen.getByRole('button', { name: 'Reports' });
    await user.click(reportsButton);
    expect(screen.getByRole('heading', { name: 'Reports' })).toBeInTheDocument();
    expect(reportsButton).toHaveAttribute('aria-current', 'page');
  });

  it('shows visible status slots for certification and freshness on reports', async () => {
    const user = userEvent.setup();
    render(<AppShell />);
    await user.click(screen.getByRole('button', { name: 'Reports' }));
    expect(screen.getByText('Certification:')).toBeInTheDocument();
    expect(screen.getByText('Freshness:')).toBeInTheDocument();
  });

  it('keeps the layout single-axis at doubled zoom (fluid width, no fixed heights)', () => {
    render(<AppShell />);
    const shell = document.querySelector('.shell');
    expect(shell).not.toBeNull();
    // jsdom does not load stylesheets: assert the fluid-layout rules directly from the
    // stylesheet source - rem-based max width, unitless padding, no fixed heights.
    const css = readFileSync('src/styles/shell.css', 'utf8');
    expect(css).toContain('max-width: 72rem');
    expect(css).toContain('padding: 0 1rem');
    expect(css).not.toMatch(/\.shell[^}]*height:\s*\d+px/);
  });
});
