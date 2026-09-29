<template><div /></template>
<script>
import SharedListMixin from '../mixins/SharedListMixin.js'

export default {
  name: 'StockHistoryPage',
  mixins: [SharedListMixin],
  data() {
    return { url: { list: '/stock/history' } }
  },
  methods: {
    refreshHistory() {
      return this.loadData(1)
    },
  },
}
</script>
