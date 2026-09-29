<template><div /></template>
<script>
import SharedListMixin from '../mixins/SharedListMixin.js'

export default {
  name: 'OrderHistoryPage',
  mixins: [SharedListMixin],
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
