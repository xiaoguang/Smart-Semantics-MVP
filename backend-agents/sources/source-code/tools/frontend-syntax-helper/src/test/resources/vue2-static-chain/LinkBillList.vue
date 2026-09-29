<template>
  <button @click="purchaseShow">选择关联单据</button>
</template>

<script>
import { JeecgListMixin } from '../mixins/JeecgListMixin.js'

export default {
  mixins: [JeecgListMixin],
  data() {
    return {
      url: { list: '/depotHead/list' },
      queryParam: {},
    }
  },
  methods: {
    purchaseShow(type, source, category, status, purchaseStatus) {
      this.queryParam.status = status
      this.queryParam.purchaseStatus = purchaseStatus
      this.loadData(1)
    },
  },
}
</script>
