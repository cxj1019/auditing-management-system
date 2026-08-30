import request from './request'
import type { ExchangeRateRow } from '@/types'

/** 中国银行外汇牌价（每 100 外币兑人民币，当日缓存） */
export function getBocRates(currencyName?: string): Promise<ExchangeRateRow[]> {
  return request.get('/exchange-rates', { params: currencyName ? { currencyName } : {} })
}
