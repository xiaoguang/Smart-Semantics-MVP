export default {
  methods: {
    refreshList() {
      return this.$refs.linkBillList.loadData(1)
    },
  },
}
