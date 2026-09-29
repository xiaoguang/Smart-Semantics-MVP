import axios from 'axios'

export function getAction(url, params) {
  return axios({ method: 'get', url, params })
}
