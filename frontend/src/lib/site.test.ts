import { describe, expect, it } from 'vitest';

import { browser, isBayflowUrl, publicSiteUrl, siteOf, workshopUrl } from './site';

describe('which site an address serves', () => {
  it('reads the workshop from <slug>.<base domain>', () => {
    expect(siteOf('acme.localhost')).toEqual({ kind: 'workshop', slug: 'acme' });
    expect(siteOf('Demo.LocalHost.')).toEqual({ kind: 'workshop', slug: 'demo' });
    expect(siteOf('kiwi.bayflow.app', 'bayflow.app')).toEqual({ kind: 'workshop', slug: 'kiwi' });
  });

  it('treats the base domain and anything else as the public site', () => {
    expect(siteOf('localhost')).toEqual({ kind: 'public' });
    expect(siteOf('www.localhost')).toEqual({ kind: 'public' });
    expect(siteOf('a.b.localhost')).toEqual({ kind: 'public' });
    expect(siteOf('127.0.0.1')).toEqual({ kind: 'public' });
  });
});

describe('addresses the app navigates to', () => {
  it('builds workshop and public-site URLs on this scheme and port', () => {
    expect(workshopUrl('acme', '/login')).toBe('http://acme.localhost:3000/login');
    expect(publicSiteUrl('/signup')).toBe('http://localhost:3000/signup');
  });

  it('never turns typed text into an address outside Bayflow', () => {
    for (const slug of ['evil.example/x?', 'a@evil.example', 'acme.localhost', '', 'javascript:alert(1)', 'ACME']) {
      expect(() => workshopUrl(slug)).toThrow();
    }
    for (const path of ['//evil.example', 'https://evil.example', 'login', '/\\evil.example']) {
      expect(() => workshopUrl('acme', path)).toThrow();
    }
  });

  it('only navigates to Bayflow addresses', () => {
    expect(isBayflowUrl('http://acme.localhost:3000/login')).toBe(true);
    expect(isBayflowUrl('http://localhost:3000/')).toBe(true);
    expect(isBayflowUrl('http://evil.example:3000/')).toBe(false);
    expect(isBayflowUrl('http://a.b.localhost:3000/')).toBe(false);
    expect(isBayflowUrl('http://evil.example@acme.localhost:3000/')).toBe(false);
    expect(isBayflowUrl('http://acme.localhost:4000/')).toBe(false);
    expect(isBayflowUrl('javascript:alert(1)')).toBe(false);
    // Refused before the browser is asked to go anywhere.
    expect(() => browser.go('https://evil.example/')).toThrow('Refusing to navigate outside Bayflow');
  });
});
