import request from './request'

export interface AiSettings { baseUrl?: string; apiKey?: string; model?: string; configured?: boolean }

export function getAiSettings(): Promise<AiSettings> {
  return request.get('/system/ai-settings')
}

export function saveAiSettings(data: { baseUrl: string; apiKey: string; model: string }): Promise<void> {
  return request.put('/system/ai-settings', data)
}
