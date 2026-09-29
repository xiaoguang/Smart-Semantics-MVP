import { axios } from '@/utils/request'

export function getAction(url, parameter) {
  return axios({ url: url, method: 'get', params: parameter })
}
