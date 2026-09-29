<template><div /></template>
<script>
import ExcludedListMixin from '../mixins/ExcludedListMixin.js'

export default {
  name: 'ExcludedMixinPage',
  mixins: [ExcludedListMixin],
  data() {
    return { url: { list: '/excluded/list' } }
  },
}
</script>
