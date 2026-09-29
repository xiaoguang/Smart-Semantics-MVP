<template>
  <div class="link-bill-list" />
</template>

<script>
import SharedListMixin from '../mixins/SharedListMixin.js'

export default {
  name: 'LinkBillList',
  mixins: [SharedListMixin],
  data() {
    return { url: { list: '/depotHead/list' } }
  },
  methods: {
    purchaseShow(type, source, category, status, number) {
      return this.loadData(1)
    },
  },
}
</script>
