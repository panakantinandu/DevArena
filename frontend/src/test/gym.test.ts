import { describe, expect, it } from 'vitest'
import { parseAmount, sci } from '@/pages/gym/model'

describe('parseAmount', () => {
  it('accepts plain, grouped, scientific and suffixed numbers', () => {
    expect(parseAmount('23000')).toBe(23000)
    expect(parseAmount('23,000')).toBe(23000)
    expect(parseAmount('2.3e4')).toBe(23000)
    expect(parseAmount('23k')).toBe(23000)
    expect(parseAmount('1.5M')).toBe(1_500_000)
    expect(parseAmount('2bn')).toBe(2e9)
    expect(parseAmount('2B')).toBe(2e9)
    expect(parseAmount('55T')).toBe(55e12)
    expect(parseAmount(' 7 ')).toBe(7)
  })

  it('rejects empty, negative, zero and junk input', () => {
    for (const bad of ['', '0', '-5', 'abc', '1..2', '5x', 'e5']) expect(parseAmount(bad)).toBeNull()
  })
})

describe('sci', () => {
  it('formats small numbers plainly and large ones in scientific notation', () => {
    expect(sci(20)).toBe('20')
    expect(sci(23148)).toBe('2.3 × 10⁴')
    expect(sci(5.475e13)).toBe('5.5 × 10¹³')
  })
})
