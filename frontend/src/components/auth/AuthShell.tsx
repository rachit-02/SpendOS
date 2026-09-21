import type { ReactNode } from 'react'
import { ShieldCheck } from 'lucide-react'
import { Logo } from '@/components/common/Logo'

/** Split-screen frame for the login and register pages. */
export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      <section className="hidden flex-col justify-between bg-gradient-to-br from-indigo-600 via-indigo-700 to-violet-800 p-10 text-white lg:flex">
        <Logo inverted />
        <div className="space-y-4">
          <h2 className="text-3xl font-semibold leading-tight">Understand where your money goes — and why.</h2>
          <p className="max-w-md text-indigo-100">
            Import your statements, see explainable insights, catch money leaks and plan with confidence.
          </p>
        </div>
        <p className="flex items-center gap-2 text-sm text-indigo-100">
          <ShieldCheck className="h-4 w-4" aria-hidden="true" />
          We never ask for bank passwords, UPI PINs or OTPs.
        </p>
      </section>
      <section className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm space-y-6">
          <div className="lg:hidden">
            <Logo />
          </div>
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold">{title}</h1>
            <p className="text-sm text-muted-foreground">{subtitle}</p>
          </div>
          {children}
        </div>
      </section>
    </main>
  )
}
