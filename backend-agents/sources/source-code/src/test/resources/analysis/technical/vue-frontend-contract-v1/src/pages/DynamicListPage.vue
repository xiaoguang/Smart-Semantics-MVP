<template><div /></template>
<script>
import SharedListMixin from '../mixins/SharedListMixin.js'

export default {
  name: 'DynamicListPage',
  mixins: [SharedListMixin],
  data() {
    return { url: { list: this.$route.meta.listEndpoint } }
  },
  methods: {
    loadHistory() {
      return this.loadData(1)
    },
  },
}
</script>
