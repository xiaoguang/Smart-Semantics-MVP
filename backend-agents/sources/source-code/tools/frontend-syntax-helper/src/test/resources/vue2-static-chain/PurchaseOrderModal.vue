<template>
  <section>
    <link-bill-list
      ref="linkBillList"
      @search="onSearchLinkApply"
      @search-number="onSearchLinkNumber"
    ></link-bill-list>
  </section>
</template>

<script>
import LinkBillList from '../dialog/LinkBillList'
import BillModalMixin from '../mixins/BillModalMixin.js'

export default {
  components: { LinkBillList },
  mixins: [BillModalMixin],
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
