import { axios } from '../utils/request.js'

export function getAction(url, parameter) {
  return axios({ url: url, method: 'get', params: parameter })
}

export function saveAction(url, method, payload) {
  return axios({ url: url, method, data: payload })
}
