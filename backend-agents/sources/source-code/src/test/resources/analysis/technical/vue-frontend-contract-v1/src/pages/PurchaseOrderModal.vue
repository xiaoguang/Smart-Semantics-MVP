<template>
  <section>
    <link-bill-list
      ref="linkBillList"
      @search="onSearchLinkApply"
      @search-number="onSearchLinkNumber"
    />
  </section>
</template>

<script>
import LinkBillList from '../components/LinkBillList.vue'

export default {
  name: 'PurchaseOrderModal',
  components: { LinkBillList },
  methods: {
    onSearchLinkApply() {
      this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '1,3')
    },
    onSearchLinkNumber() {
      this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '0,3', 'number')
    },
  },
}
</script>
