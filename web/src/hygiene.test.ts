import { describe, expect, it } from 'vitest'

/**
 * `detail` is the server's English sentence. It exists for the log and the
 * copy-details block. Rendering it puts English in the middle of a German
 * dashboard, which is exactly what it used to do at ChildDetail.tsx:50.
 */
describe("the dashboard never renders the server's detail", () => {
  const sources = import.meta.glob(['./pages/*.tsx', './components/*.tsx'], {
    query: '?raw',
    eager: true,
  }) as Record<string, { default: string }>

  it('has sources to check', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(5)
  })

  for (const [path, module] of Object.entries(sources)) {
    if (path.includes('.test.')) continue
    it(`${path} does not read .detail`, () => {
      expect(module.default).not.toMatch(/\.detail\b/)
    })
  }
})
