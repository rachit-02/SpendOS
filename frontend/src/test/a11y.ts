import axe from 'axe-core'

/**
 * Runs axe-core (WCAG 2.1 A/AA rules) on a rendered tree and returns readable violations.
 * Colour contrast needs real layout and is checked in the browser; jsdom cannot compute it.
 */
export async function a11yViolations(node: Element): Promise<string[]> {
  const result = await axe.run(node, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } },
  })
  return result.violations.map((v) => `${v.id}: ${v.help} -> ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)
}
