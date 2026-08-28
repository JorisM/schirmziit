import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { QrCode } from './QrCode'

const tiny = {
  size: 3,
  rows: ['101', '010', '101'],
}

describe('QrCode', () => {
  it('draws one module per dark cell, at the coordinates it was given', () => {
    render(<QrCode matrix={tiny} label="pairing code" />)

    const path = screen.getByRole('img').querySelector('path')
    // Five dark modules, at the five positions the matrix marks. A renderer
    // that transposes x and y, or offsets by one, still draws a plausible
    // square — and a plausible square scans as nothing.
    expect(path?.getAttribute('d')).toBe(
      'M0 0h1v1h-1zM2 0h1v1h-1zM1 1h1v1h-1zM0 2h1v1h-1zM2 2h1v1h-1z',
    )
  })

  it('sizes its viewBox to the matrix, quiet zone and all', () => {
    render(<QrCode matrix={tiny} label="pairing code" />)

    // The quiet zone is part of `rows`. A viewBox tighter than the matrix
    // would crop the border a scanner looks for.
    expect(screen.getByRole('img').getAttribute('viewBox')).toBe('0 0 3 3')
  })

  it('is announced, not left as decoration', () => {
    render(<QrCode matrix={tiny} label="QR code with the address and the code" />)

    expect(screen.getByLabelText('QR code with the address and the code')).toBeTruthy()
  })
})
