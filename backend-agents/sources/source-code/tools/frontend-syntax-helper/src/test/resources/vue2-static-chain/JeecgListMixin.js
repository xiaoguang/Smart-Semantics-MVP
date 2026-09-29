import { getAction } from '@/api/manage'

export const JeecgListMixin = {
  methods: {
    loadData(page) {
      let params = this.getQueryParams()
      getAction(this.url.list, params).then((response) => {
        this.dataSource = response.data
      })
    },
    getQueryParams() {
      return { pageNo: 1 }
    },
  },
}
