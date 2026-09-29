import { getAction } from '../utils/request.js'

export default {
  methods: {
    loadData(page) {
      return getAction(this.url.list, { page })
    },
  },
}
