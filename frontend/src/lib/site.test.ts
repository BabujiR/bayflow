import { describe, expect, it } from 'vitest';

import { siteOf } from './site';

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
