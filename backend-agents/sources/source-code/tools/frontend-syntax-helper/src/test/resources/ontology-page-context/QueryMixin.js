import { getAction } from '../api/client.js'

export const QueryMixin = {
  methods: {
    loadData(page) {
      const params = this.getQueryParams()
      return getAction(this.url.list, params)
    },
    getQueryParams() {
      return { page: 1 }
    },
  },
}
