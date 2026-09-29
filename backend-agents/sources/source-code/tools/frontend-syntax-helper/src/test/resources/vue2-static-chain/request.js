import axios from 'axios'

let apiBaseUrl = window._CONFIG['domianURL'] || '/jshERP-boot'
const service = axios.create({ baseURL: apiBaseUrl })

export { service as axios }
