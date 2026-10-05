const apiBaseUrl = window._CONFIG['recordsURL'] || '/records-api'
const service = axios.create({ baseURL: apiBaseUrl })

export { service as axios }
