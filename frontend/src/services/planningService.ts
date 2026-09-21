import { api, toApiError, unwrap } from './api'
import type { MonthValue } from '@/utils/months'
import type {
  AffordabilityResult,
  Goal,
  GoalInput,
  Scenario,
  Simulation,
  SimulationComparison,
  SpendingPrediction,
} from '@/types/planning'

async function remove(url: string) {
  try {
    await api.delete(url)
  } catch (error) {
    throw toApiError(error)
  }
}

export const planningService = {
  prediction: (month?: MonthValue) =>
    unwrap<SpendingPrediction>(api.get('/reports/spending-prediction', { params: month ?? {} })),
  affordability: (purchaseAmount: number, purchaseDescription?: string) =>
    unwrap<AffordabilityResult>(api.post('/reports/affordability', { purchaseAmount, purchaseDescription })),
}

export const goalService = {
  list: () => unwrap<Goal[]>(api.get('/goals')),
  create: (input: GoalInput) => unwrap<Goal>(api.post('/goals', input)),
  update: (id: string, input: GoalInput) => unwrap<Goal>(api.put(`/goals/${id}`, input)),
  contribute: (id: string, amount: number) => unwrap<Goal>(api.post(`/goals/${id}/contributions`, { amount })),
  remove: (id: string) => remove(`/goals/${id}`),
}

export const simulationService = {
  list: () => unwrap<Simulation[]>(api.get('/simulations')),
  create: (simulationName: string, scenarios: Scenario[]) =>
    unwrap<Simulation>(api.post('/simulations', { simulationName, scenarios })),
  compare: (ids: string[]) =>
    unwrap<SimulationComparison>(api.get('/simulations/compare', { params: { ids: ids.join(',') } })),
  remove: (id: string) => remove(`/simulations/${id}`),
}
