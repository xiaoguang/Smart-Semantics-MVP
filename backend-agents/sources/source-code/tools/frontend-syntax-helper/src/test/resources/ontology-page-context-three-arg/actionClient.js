import { axios } from '../utils/transport.js'

export function httpAction(url, parameter, method) {
  return axios({
    url: url,
    method: method,
    data: parameter,
  })
}
