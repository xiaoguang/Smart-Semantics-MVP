import axiosFactory from 'axios'

const service = axiosFactory.create({ baseURL: '/canvas-api' })

export { service as axios }
