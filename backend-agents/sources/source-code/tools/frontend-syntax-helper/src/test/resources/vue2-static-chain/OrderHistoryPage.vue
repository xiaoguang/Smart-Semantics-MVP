<template><div /></template>

<script>
import { JeecgListMixin } from '../mixins/JeecgListMixin.js'

export default {
  name: 'OrderHistoryPage',
  mixins: [JeecgListMixin],
  data() {
    return { url: { list: '/orders/history' } }
  },
  methods: {
    refreshHistory() {
      return this.loadData(1)
    },
  },
}
</script>
